/*
 * Copyright (c) 2021 Auxio Project
 * LangUtil.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.util

import kotlin.reflect.KClass
import org.oxycblt.auxio.BuildConfig

/**
 * Sanitizes a value that is unlikely to be null. On debug builds, this aliases to [requireNotNull],
 * otherwise, it aliases to the unchecked dereference operator (!!). This can be used as a minor
 * optimization in certain cases.
 */
fun <T> unlikelyToBeNull(value: T?) =
    if (BuildConfig.DEBUG) {
        requireNotNull(value)
    } else {
        value!!
    }

/**
 * Aliases a check to ensure that the given number is non-zero.
 *
 * @return The given number if it's non-zero, null otherwise.
 */
fun Int.positiveOrNull() = if (this > 0) this else null

/**
 * Lazily set up a reflected field. Automatically handles visibility changes. Adapted from Material
 * Files: https://github.com/zhanghai/MaterialFiles
 *
 * @param clazz The [KClass] to reflect into.
 * @param field The name of the field to obtain.
 */
fun lazyReflectedField(clazz: KClass<*>, field: String) = lazy {
    clazz.java.getDeclaredField(field).also { it.isAccessible = true }
}

/**
 * Lazily set up a reflected method. Automatically handles visibility changes. Adapted from Material
 * Files: https://github.com/zhanghai/MaterialFiles
 *
 * @param clazz The [KClass] to reflect into.
 * @param method The name of the method to obtain.
 */
fun lazyReflectedMethod(clazz: KClass<*>, method: String, vararg params: KClass<*>) = lazy {
    clazz.java.getDeclaredMethod(method, *params.map { it.java }.toTypedArray()).also {
        it.isAccessible = true
    }
}

/**
 * Computes the Jaro-Winkler string similarity between two char sequences.
 *
 * @return A similarity score between 0.0 (no similarity) and 1.0 (exact match).
 */
fun jaroWinklerSimilarity(s1: CharSequence, s2: CharSequence): Double {
    if (s1 == s2) return 1.0
    if (s1.isEmpty() || s2.isEmpty()) return 0.0

    val maxLen = maxOf(s1.length, s2.length)
    val matchWindow = maxOf(0, (maxLen / 2) - 1)

    val s1Matches = BooleanArray(s1.length)
    val s2Matches = BooleanArray(s2.length)

    var matches = 0
    for (i in s1.indices) {
        val start = maxOf(0, i - matchWindow)
        val end = minOf(i + matchWindow + 1, s2.length)
        for (j in start until end) {
            if (!s2Matches[j] && s1[i] == s2[j]) {
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }
    }

    if (matches == 0) return 0.0

    var transpositions = 0
    var k = 0
    for (i in s1.indices) {
        if (!s1Matches[i]) continue
        while (!s2Matches[k]) {
            k++
        }
        if (s1[i] != s2[k]) {
            transpositions++
        }
        k++
    }

    val jaro =
        ((matches.toDouble() / s1.length) +
            (matches.toDouble() / s2.length) +
            ((matches - transpositions / 2.0) / matches)) / 3.0

    var prefix = 0
    val maxPrefix = minOf(4, minOf(s1.length, s2.length))
    while (prefix < maxPrefix && s1[prefix] == s2[prefix]) {
        prefix++
    }

    return jaro + prefix * 0.1 * (1.0 - jaro)
}
