package io.nekohasekai.sagernet.ui

/**
 * Resolve a chain proxy's display string from its node profile IDs.
 *
 * Returns the node display names in chain order, joined with " → ",
 * skipping IDs that have no name (deleted nodes, or nodes from another
 * group that is not in the current list). Returns "" when no node resolves.
 *
 * Pure function: the caller supplies the ID → name map (built from the
 * already-loaded in-memory profile list), so no database access happens
 * here and the list can cache the result per chain ID.
 */
fun resolveChainDisplayNames(proxies: List<Long>, nameById: Map<Long, String>): String {
    return proxies.mapNotNull { nameById[it] }.joinToString(" → ")
}

/**
 * Collect the node IDs referenced by chains that have no name in the
 * current group's in-memory map — typically nodes living in another
 * group. The caller resolves these with one batched database lookup and
 * merges the names into the map before calling resolveChainDisplayNames.
 *
 * Pure function: no database access here.
 */
fun collectMissingChainNodeIds(
    chainProxies: Collection<List<Long>>,
    nameById: Map<Long, String>,
): Set<Long> {
    val missing = mutableSetOf<Long>()
    for (proxies in chainProxies) {
        for (id in proxies) {
            if (id !in nameById) missing.add(id)
        }
    }
    return missing
}
