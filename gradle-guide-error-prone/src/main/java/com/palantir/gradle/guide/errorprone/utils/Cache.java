/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.guide.errorprone.utils;

import com.google.errorprone.VisitorState;
import com.google.errorprone.suppliers.Supplier;
import java.lang.ref.SoftReference;

/**
 * Cache which depends on the {@code VisitorState}.
 * This cache depends on the current Compilation Unit, c.f. {@code VisitorState#memoize}, which doesn't.
 */
public final class Cache<T> implements Supplier<T> {
    private final Supplier<T> impl;
    private SoftReference<T> cache = new SoftReference<>(null);

    private Cache(Supplier<T> impl) {
        this.impl = impl;
    }

    @Override
    public synchronized T get(VisitorState state) {
        T value = cache.get();
        if (value == null) {
            value = impl.get(state);
            if (value != null) {
                cache = new SoftReference<>(value);
            }
        }
        return value;
    }

    /**
     * Produces a cache for a function.
     */
    public static <T> Supplier<T> memoize(Supplier<T> func) {
        return new Cache<>(func);
    }
}
