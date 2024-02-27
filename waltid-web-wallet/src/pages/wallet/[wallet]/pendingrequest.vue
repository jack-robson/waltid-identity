<template>
    <CenterMain>
        <h1 class="text-2xl font-semibold mb-2">Pending Request</h1>

        <p class="mb-1">The list of pending requests is shown below:</p>

        <div v-if="pendingRequests.length" aria-label="Credential list" class="h-full overflow-y-auto shadow-xl">
            <div class="container mx-auto px-4 sm:px-8">
                <div class="py-8">
                    <!-- table source: https://codepen.io/superfly/pen/wvvPLZB -->
                    <div>
                        <h2 class="text-2xl font-semibold leading-tight">List of pending requests</h2>
                    </div>
                    <div class="-mx-4 sm:-mx-8 px-4 sm:px-8 py-4 overflow-x-auto">
                        <div class="inline-block min-w-full shadow-md rounded-lg overflow-hidden">
                            <table class="min-w-full leading-normal">
                                <thead>
                                    <tr>
                                        <th
                                            class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider">
                                            Timestamp
                                        </th>
                                        <th
                                            class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider">
                                            Event
                                        </th>
                                        <th
                                            class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider">
                                            Action
                                        </th>
                                        <th class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100"></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <template v-for="item in pendingRequests" :key="item">
                                        <tr>
                                            <td class="px-5 py-5 border-b border-gray-200 bg-white text-sm">
                                                <div class="flex">
                                                    <div class="ml-3">
                                                        <p class="text-gray-900 whitespace-no-wrap">
                                                            {{ new Date(item.addedOn).toLocaleString() }}
                                                        </p>
                                                    </div>
                                                </div>
                                            </td>
                                            <td class="px-5 py-5 border-b border-gray-200 bg-white text-sm">
                                                <p class="text-gray-900 whitespace-no-wrap">
                                                    Credential Issuance
                                                </p>
                                            </td>

                                            <td class="px-5 py-5 border-b border-gray-200 bg-white text-sm flex gap-2">
                                                <button
                                                    class="bg-green-500 hover:bg-green-700 text-white font-bold py-2 px-4 rounded"
                                                    @click="acceptRequest(item)">
                                                    Accept
                                                </button>
                                                <button
                                                    class="bg-red-500 hover:bg-red-700 text-white font-bold py-2 px-4 rounded"
                                                    @click="rejectRequest(item)">
                                                    Reject
                                                </button>
                                            </td>
                                        </tr>
                                    </template>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <div v-else class="mt-8 border-l pl-2">
            <h3 class="mt-2 text-base font-semibold text-gray-900">No pending requests</h3>
            <p class="text-base text-gray-500">No pending requests found.</p>
        </div>
    </CenterMain>
</template>

<script setup>
import CenterMain from "~/components/CenterMain.vue";

const currentWallet = useCurrentWallet()

console.log("Loading pending requests...")
const pendingRequests = await $fetch(`/wallet-api/wallet/${currentWallet.value}/api/notifications/pending`);

async function acceptRequest(item) {
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/credentials/${item.parsedDocument.id}/accept`, {
        method: "POST",
    });
    location.reload();
}

async function rejectRequest(item) {
    await $fetch(`/wallet-api/wallet/${currentWallet.value}/credentials/${item.parsedDocument.id}/reject`, {
        method: "POST",
    });
    location.reload();
}

</script>

<style scoped></style>
