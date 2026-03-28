<script lang="ts">
	import { m } from '$lib/paraglide/messages.js';
	import IconCard from '$lib/IconCard.svelte';
	import { TaskListIcon } from '@indaco/svelte-iconoir/task-list';
	import { EuroIcon } from '@indaco/svelte-iconoir/euro';
	import { StatsReportIcon } from '@indaco/svelte-iconoir/stats-report';
</script>

<div class="hero min-h-screen bg-base-200">
	<div class="hero-content">
		<div class="md:card md:w-96 md:bg-base-100 md:shadow-sm">
			<div class="md:card-body">
				<div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-primary">
					<span class="text-xl font-bold text-white">LH</span>
				</div>
				<h1 class="text-center text-2xl font-bold text-base-content">LibreHousehold</h1>
				<p class="mt-2 text-center text-base-content/70">{m['subtitle']()}</p>
				<div class="flex flex-col justify-around gap-5">
					<h2 class="text-xl font-bold text-base-content">{m['welcome.title']()}</h2>
					<p>{m['welcome.text']()}</p>
					<IconCard
						icon={TaskListIcon}
						title={m['welcome.feature_cards.card_1.title']()}
						description={m['welcome.feature_cards.card_1.description']()}
					/>
					<IconCard
						icon={EuroIcon}
						title={m['welcome.feature_cards.card_2.title']()}
						description={m['welcome.feature_cards.card_2.description']()}
					/>
					<IconCard
						icon={StatsReportIcon}
						title={m['welcome.feature_cards.card_3.title']()}
						description={m['welcome.feature_cards.card_3.description']()}
					/>
					<a class="btn w-full rounded-lg p-6 btn-primary" href="/setup"
						>{m['welcome.setup_button']()}</a
					>
					<a class="btn w-full rounded-lg p-6 btn-primary" href="/login"
						>{m['welcome.login_button']()}</a
					>
				</div>
			</div>
		</div>
	</div>
</div>
