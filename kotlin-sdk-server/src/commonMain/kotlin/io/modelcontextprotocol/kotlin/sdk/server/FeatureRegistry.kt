package io.modelcontextprotocol.kotlin.sdk.server

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentSet

/**
 * A listener interface for receiving notifications about feature changes in registry.
 */
internal interface FeatureListener {
    fun onFeatureUpdated(featureKey: String)
}

/**
 * A generic registry for managing features of a specified type. This class provides thread-safe
 * operations for adding, removing, and retrieving features from the registry.
 *
 * @param T The type of the feature, constrained to implement the [Feature] interface.
 * @param featureType A string description of the type of feature being managed.
 *                    Used primarily for logging purposes.
 */
internal class FeatureRegistry<T : Feature>(private val featureType: String) {

    private val logger = KotlinLogging.logger(name = "FeatureRegistry[$featureType]")

    /**
     * Atomic variable used to maintain a thread-safe registry of features.
     * Stores a persistent map where each feature is identified by a unique key.
     */
    private val registry = atomic(persistentMapOf<FeatureKey, T>())

    /**
     * Provides a snapshot of all features currently registered in the registry.
     * Keys represent unique identifiers for each feature, and values represent the associated features.
     */
    internal val values: Map<FeatureKey, T>
        get() = registry.value

    private val listeners = atomic(persistentListOf<FeatureListener>())

    internal fun addListener(listener: FeatureListener) {
        listeners.update { it.add(listener) }
    }

    internal fun removeListener(listener: FeatureListener) {
        listeners.update { it.remove(listener) }
    }

    /**
     * Adds the specified feature to the registry.
     *
     * @param feature The feature to be added to the registry.
     */
    internal fun add(feature: T) {
        logger.info { "Adding $featureType: \"${feature.key}\"" }
        val oldMap = registry.getAndUpdate { current -> current.put(feature.key, feature) }
        val oldFeature = oldMap[feature.key]

        logger.info { "Added $featureType: \"${feature.key}\"" }
        notifyFeatureUpdated(oldFeature, feature)
    }

    /**
     * Adds the given list of features to the registry. Each feature is mapped by its key
     * and added to the current registry.
     *
     * @param features The list of features to add to the registry.
     */
    internal fun addAll(features: List<T>) {
        logger.info { "Adding ${featureType}s: ${features.size}" }
        val oldMap = registry.getAndUpdate { current -> current.putAll(features.associateBy { it.key }) }

        logger.info { "Added ${featureType}s: ${features.size}" }
        for (feature in features) {
            val oldFeature = oldMap[feature.key]
            notifyFeatureUpdated(oldFeature, feature)
        }
    }

    /**
     * Removes the feature associated with the given key from the registry.
     *
     * @param key The key of the feature to be removed.
     * @return `true` if the feature was successfully removed, or `false` if the feature was not found.
     */
    internal fun remove(key: FeatureKey): Boolean {
        logger.info { "Removing $featureType: \"$key\"" }
        val oldMap = registry.getAndUpdate { current -> current.remove(key) }

        val removedFeature = oldMap[key]
        val removed = removedFeature != null

        if (removed) {
            logger.info { "Removed $featureType: \"$key\"" }
            notifyFeatureUpdated(removedFeature, null)
        } else {
            logger.info { "$featureType not found: \"$key\"" }
        }

        return removed
    }

    /**
     * Removes the features associated with the given keys from the registry.
     *
     * @param keys The list of keys whose associated features are to be removed.
     * @return The number of features that were successfully removed.
     */
    internal fun removeAll(keys: List<FeatureKey>): Int {
        logger.info { "Removing ${featureType}s: ${keys.size}" }
        val oldMap = registry.getAndUpdate { current -> current - keys.toPersistentSet() }

        val removedFeatures = keys.mapNotNull { oldMap[it] }
        val removedCount = removedFeatures.size

        if (removedCount > 0) {
            logger.info { "Removed ${featureType}s: $removedCount" }
        } else {
            logger.info { "No $featureType were removed" }
        }
        removedFeatures.forEach {
            notifyFeatureUpdated(it, null)
        }

        return removedCount
    }

    /**
     * Retrieves the feature associated with the given key from the registry.
     *
     * @param key The key of the feature to retrieve.
     * @return The feature associated with the given key, or `null` if no such feature exists in the registry.
     */
    internal fun get(key: FeatureKey): T? {
        logger.info { "Getting $featureType: \"$key\"" }
        val feature = registry.value[key]
        if (feature != null) {
            logger.info { "Got $featureType: \"$key\"" }
        } else {
            logger.info { "$featureType not found: \"$key\"" }
        }

        return feature
    }

    private fun notifyFeatureUpdated(oldFeature: T?, newFeature: T?) {
        val featureKey = (oldFeature?.key ?: newFeature?.key) ?: run {
            logger.error { "Notification should have feature key, but none found" }
            return
        }

        logger.info { "Notifying listeners on feature update" }
        listeners.value.forEach { it.onFeatureUpdated(featureKey) }
    }
}
