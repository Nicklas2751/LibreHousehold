<script lang="ts">
    import {m} from '$lib/paraglide/messages.js';
    import IconCard from "$lib/IconCard.svelte";
    import {TaskListIcon} from "@indaco/svelte-iconoir/task-list";
    import {EuroIcon} from "@indaco/svelte-iconoir/euro";
    import {StatsReportIcon} from "@indaco/svelte-iconoir/stats-report";
</script>

<div class="hero bg-base-200 min-h-screen">
    <div class="hero-content">
        <div class="md:card md:bg-base-100 md:w-96 md:shadow-sm">
            <div class="md:card-body">
                <div class="w-12 h-12 rounded-lg bg-primary flex items-center justify-center mx-auto mb-4">
                    <span class="text-white font-bold text-xl">LH</span>
                </div>
                <h1 class="text-2xl font-bold text-base-content text-center">LibreHousehold</h1>
                <p class="text-base-content/70 mt-2 text-center">{m['subtitle']()}</p>
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
                    <a class="btn rounded-lg btn-primary w-full p-6"
                       href="/setup">{m['welcome.setup_button']()}</a>
                    <a class="btn rounded-lg btn-primary w-full p-6"
                       href="/login">{m['welcome.login_button']()}</a>
                </div>
            </div>
        </div>
    </div>
</div>