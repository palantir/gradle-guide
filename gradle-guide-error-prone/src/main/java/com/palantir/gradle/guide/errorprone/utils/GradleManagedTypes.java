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
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.tools.javac.code.Type;

public final class GradleManagedTypes {
    private static final Supplier<Type> PROVIDER_TYPE_SUPPLIER =
            Suppliers.typeFromString("org.gradle.api.provider.Provider");
    private static final Supplier<Type> DOMAIN_OBJECT_COLLECTION_TYPE_SUPPLIER =
            Suppliers.typeFromString("org.gradle.api.DomainObjectCollection");
    private static final Supplier<Type> FILE_COLLECTION_TYPE_SUPPLIER =
            Suppliers.typeFromString("org.gradle.api.file.FileCollection");

    public static boolean isManagedType(Type type, VisitorState state) {
        return isSubtypeOfProvider(type, state)
                || isSubtypeOfDomainObjectCollection(type, state)
                || isSubtypeOfFileCollection(type, state);
    }

    private static boolean isSubtypeOfProvider(Type type, VisitorState state) {
        return ASTHelpers.isSubtype(type, PROVIDER_TYPE_SUPPLIER.get(state), state);
    }

    private static boolean isSubtypeOfDomainObjectCollection(Type type, VisitorState state) {
        return ASTHelpers.isSubtype(type, DOMAIN_OBJECT_COLLECTION_TYPE_SUPPLIER.get(state), state);
    }

    private static boolean isSubtypeOfFileCollection(Type type, VisitorState state) {
        return ASTHelpers.isSubtype(type, FILE_COLLECTION_TYPE_SUPPLIER.get(state), state);
    }

    private GradleManagedTypes() {}
}
