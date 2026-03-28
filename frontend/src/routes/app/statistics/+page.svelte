<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import PageTitle from '$lib/PageTitle.svelte';
	import { householdState } from '$lib/stores/householdState.svelte';
	import {
		loadStatistics,
		statistics,
		statisticsLoading,
		statisticsError,
		GetStatisticsPeriodEnum,
		type StatisticsPeriod
	} from '$lib/stores/statisticsStore';

	const periods: { key: StatisticsPeriod; label: string }[] = [
		{ key: GetStatisticsPeriodEnum.Last7Days, label: m['statistics.periods.last_7_days']() },
		{ key: GetStatisticsPeriodEnum.Last14Days, label: m['statistics.periods.last_14_days']() },
		{ key: GetStatisticsPeriodEnum.ThisMonth, label: m['statistics.periods.this_month']() },
		{ key: GetStatisticsPeriodEnum.Last3Months, label: m['statistics.periods.last_3_months']() },
		{ key: GetStatisticsPeriodEnum.Last6Months, label: m['statistics.periods.last_6_months']() },
		{ key: GetStatisticsPeriodEnum.ThisYear, label: m['statistics.periods.this_year']() },
		{ key: GetStatisticsPeriodEnum.LastYear, label: m['statistics.periods.last_year']() }
	];

	const multiMonthPeriods: StatisticsPeriod[] = [
		GetStatisticsPeriodEnum.Last3Months,
		GetStatisticsPeriodEnum.Last6Months,
		GetStatisticsPeriodEnum.ThisYear,
		GetStatisticsPeriodEnum.LastYear
	];

	let period: StatisticsPeriod = $state(GetStatisticsPeriodEnum.ThisMonth);
	const isMultiMonth = $derived(multiMonthPeriods.includes(period));
	const periodLabel = $derived(periods.find((p) => p.key === period)?.label ?? '');

	$effect(() => {
		if ($householdState) {
			loadStatistics($householdState.id, period);
		}
	});

	const data = $derived($statistics);

	const totalCatExp = $derived(data?.expensesByCategory.reduce((s, c) => s + c.total, 0) ?? 0);

	const sliceColors = [
		'var(--color-primary)',
		'var(--color-secondary)',
		'var(--color-accent)',
		'var(--color-info)',
		'var(--color-warning)',
		'var(--color-error)'
	];

	const pieGradient = $derived.by(() => {
		if (!data || totalCatExp === 0) return '';
		let cum = 0;
		const stops = data.expensesByCategory.map((cat, i) => {
			const pct = (cat.total / totalCatExp) * 100;
			const start = cum;
			cum += pct;
			return `${sliceColors[i % sliceColors.length]} ${start.toFixed(2)}% ${cum.toFixed(2)}%`;
		});
		return `conic-gradient(${stops.join(', ')})`;
	});

	function taskRate(done: number, open: number): number {
		const t = done + open;
		return t === 0 ? 0 : Math.round((done / t) * 100);
	}
</script>

<div class="h-full space-y-6 overflow-y-auto p-4">
	<div class="flex flex-wrap items-center justify-between gap-3">
		<PageTitle title={m['statistics.title']()} />
		<select
			class="select-bordered select"
			aria-label={m['statistics.period_label']()}
			bind:value={period}
		>
			{#each periods as p (p.key)}
				<option value={p.key}>{p.label}</option>
			{/each}
		</select>
	</div>

	{#if $statisticsLoading}
		<div class="flex justify-center py-16">
			<span class="loading loading-lg loading-spinner text-primary"></span>
		</div>
	{:else if $statisticsError}
		<div class="alert alert-error">
			<span>{m['statistics.error']()}</span>
		</div>
	{:else if data}
		<!-- Summary stats -->
		<div class="stats w-full stats-vertical shadow sm:stats-horizontal">
			<div class="stat">
				<div class="stat-title">{m['statistics.total_expenses']()}</div>
				<div class="stat-value text-primary">
					{data.totalExpenses.toFixed(2)}
					{m['common.currency_symbol']()}
				</div>
				{#if isMultiMonth}
					<div class="stat-desc">
						{m['statistics.avg_abbrev']()}
						{data.avgExpensesPerMonth.toFixed(2)}
						{m['common.currency_symbol']()}
						{m['statistics.per_month_suffix']()}
					</div>
				{:else}
					<div class="stat-desc">{periodLabel}</div>
				{/if}
			</div>
			<div class="stat">
				<div class="stat-title">{m['statistics.tasks_done']()}</div>
				<div class="stat-value text-success">
					{data.tasksByMember.reduce((s, t) => s + t.done, 0)}
				</div>
				<div class="stat-desc">{periodLabel}</div>
			</div>
			<div class="stat">
				<div class="stat-title">{m['statistics.categories_count']()}</div>
				<div class="stat-value">{data.expensesByCategory.length}</div>
				<div class="stat-desc">{m['statistics.categories_with_expenses']()}</div>
			</div>
		</div>

		<!-- Per-person cards -->
		<div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
			{#each data.expensesByMember as member (member.memberId)}
				{@const tasks = data.tasksByMember.find((t) => t.memberId === member.memberId)}
				<div class="card bg-base-200 shadow-xl">
					<div class="card-body gap-4">
						<div class="flex items-center gap-3">
							<div class="placeholder avatar">
								<div class="w-12 rounded-full bg-neutral text-neutral-content">
									<span class="text-lg">{member.memberName[0]}</span>
								</div>
							</div>
							<h2 class="card-title">{member.memberName}</h2>
						</div>
						<div class="flex items-center justify-between">
							<div>
								<p class="text-xs text-base-content/60">
									{m['statistics.member_expenses_label']()}
								</p>
								<p class="text-2xl font-bold text-primary">
									{member.total.toFixed(2)}
									{m['common.currency_symbol']()}
								</p>
								{#if isMultiMonth}
									<p class="text-xs text-base-content/50">
										{m['statistics.avg_abbrev']()}
										{member.avgPerMonth.toFixed(2)}
										{m['common.currency_symbol']()}
										{m['statistics.per_month_suffix']()}
									</p>
								{/if}
							</div>
							{#if tasks}
								<div class="flex items-center gap-3">
									<div
										class="radial-progress shrink-0 text-secondary"
										style="--value:{taskRate(
											tasks.done,
											tasks.open
										)}; --size:3.5rem; --thickness:5px;"
										aria-valuenow={taskRate(tasks.done, tasks.open)}
										role="progressbar"
									>
										<span class="text-xs font-bold">{taskRate(tasks.done, tasks.open)}%</span>
									</div>
									<div class="space-y-1 text-sm">
										<div class="flex items-center gap-1">
											<span class="badge badge-xs badge-success"></span>
											<span>{tasks.done} {m['statistics.tasks_done_label']()}</span>
										</div>
										<div class="flex items-center gap-1">
											<span class="badge badge-xs badge-warning"></span>
											<span>{tasks.open} {m['statistics.tasks_open_label']()}</span>
										</div>
									</div>
								</div>
							{/if}
						</div>
					</div>
				</div>
			{/each}
		</div>

		<!-- Category donut chart -->
		<div class="card bg-base-200 shadow-xl">
			<div class="card-body">
				<h2 class="card-title">{m['statistics.expenses_by_category']()}</h2>
				<div class="mt-2 flex flex-col items-center gap-6 sm:flex-row">
					<div class="relative h-40 w-40 shrink-0">
						<div class="h-full w-full rounded-full" style="background: {pieGradient}"></div>
						<div
							class="absolute inset-[20%] flex flex-col items-center justify-center rounded-full bg-base-200"
						>
							<span class="text-xs leading-tight font-bold"
								>{totalCatExp.toFixed(0)} {m['common.currency_symbol']()}</span
							>
							<span class="text-xs leading-tight text-base-content/50"
								>{m['statistics.category_total']()}</span
							>
						</div>
					</div>
					<div class="w-full flex-1 space-y-2">
						{#each data.expensesByCategory as cat, i (cat.categoryId)}
							<div class="flex items-center gap-2">
								<div
									class="h-3 w-3 shrink-0 rounded-full"
									style="background: {sliceColors[i % sliceColors.length]}"
								></div>
								{#if cat.categoryIcon}<span class="mr-1">{cat.categoryIcon}</span>{/if}
								<span class="flex-1 text-sm">{cat.categoryName}</span>
								<span class="text-sm font-semibold"
									>{cat.total.toFixed(2)} {m['common.currency_symbol']()}</span
								>
								<span class="w-10 text-right text-xs text-base-content/50">
									{totalCatExp > 0 ? ((cat.total / totalCatExp) * 100).toFixed(0) : 0}%
								</span>
							</div>
							{#if isMultiMonth}
								<div class="pl-5 text-xs text-base-content/50">
									{m['statistics.avg_abbrev']()}
									{cat.avgPerMonth.toFixed(2)}
									{m['common.currency_symbol']()}
									{m['statistics.per_month_suffix']()}
								</div>
							{/if}
						{/each}
					</div>
				</div>
			</div>
		</div>
	{/if}
</div>
