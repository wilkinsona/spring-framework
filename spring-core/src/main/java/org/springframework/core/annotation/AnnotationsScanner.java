/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.annotation.type.DeclaredAnnotations;
import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Scanner search for {@link DeclaredAnnotations} on the hierarchy of an
 * {@link AnnotatedElement}.
 *
 * @author Phillip Webb
 * @since 5.2
 */
class AnnotationsScanner {

	private static final Map<AnnotatedElement, Results[]> cache = new ConcurrentReferenceHashMap<>();

	public static Collection<DeclaredAnnotations> scan(AnnotatedElement source,
			SearchStrategy searchStrategy) {
		int cacheIndex = searchStrategy.ordinal();
		Results[] cached = cache.get(source);
		if (cached != null && cached[cacheIndex] != null) {
			return cached[cacheIndex];
		}
		Results results = getResults(source, searchStrategy);
		if (results != Results.NONE) {
			if (cached == null) {
				cached = new Results[SearchStrategy.values().length];
				cache.put(source, cached);
			}
			cached[cacheIndex] = results;
		}
		return results;
	}

	private static Results getResults(AnnotatedElement source,
			SearchStrategy searchStrategy) {
		if (source instanceof Class) {
			return ClassAnnotationsScanner.getResults((Class<?>) source, searchStrategy);
		}
		if (source instanceof Method) {
			return MethodAnnotationsScanner.getResults((Method) source, searchStrategy);
		}
		return ElementAnnotationsScanner.getResults(source, searchStrategy);
	}

	static void clearCache() {
		cache.clear();
		TypeHierarchy.superclassesCache.clear();
		TypeHierarchy.superclassesAndInterfacesCache.clear();
		MethodAnnotationsScanner.methodsCache.clear();
	}

	private static class ClassAnnotationsScanner {

		public static Results getResults(Class<?> source, SearchStrategy searchStrategy) {
			switch (searchStrategy) {
				case DIRECT:
					return getDirect(source);
				case INHERITED_ANNOTATIONS:
					return getInheritedAnnotations(source);
				case SUPER_CLASS:
					return getWithHierarchy(TypeHierarchy::superclasses);
				case EXHAUSTIVE:
					return getWithHierarchy(TypeHierarchy::superclassesAndInterfaces);
			}
			throw new IllegalStateException(
					"Unsupported search strategy " + searchStrategy);
		}

		private static Results getDirect(Class<?> source) {
			Annotation[] annotations = source.getDeclaredAnnotations();
			if (Results.isIgnorable(annotations)) {
				return Results.NONE;
			}
			return Results.of(DeclaredAnnotations.from(source, annotations));
		}

		private static Results getInheritedAnnotations(Class<?> source) {
			Annotation[] annotations = source.getAnnotations();
			if (Results.isIgnorable(annotations)) {
				return Results.NONE;
			}
			Set<Class<?>> types = getAnnotationTypes(annotations);
			List<DeclaredAnnotations> aggregates = new ArrayList<>();
			for (Class<?> type : TypeHierarchy.superclasses(source)) {
				Set<Annotation> declaredAnnotations = new LinkedHashSet<>(types.size());
				for (Annotation candidate : type.getDeclaredAnnotations()) {
					if (types.remove(candidate.annotationType())) {
						declaredAnnotations.add(candidate);
					}
				}
				aggregates.add(DeclaredAnnotations.from(type, declaredAnnotations));
			}
			return Results.of(aggregates);
		}

		private static Set<Class<?>> getAnnotationTypes(Annotation[] annotations) {
			Set<Class<?>> types = new HashSet<>(annotations.length);
			for (Annotation annotation : annotations) {
				types.add(annotation.annotationType());
			}
			return types;
		}

		private static AnnotationsScanner createWithHierarchy(Class<?> source,
				Function<Class<?>, TypeHierarchy> hierarchyFactory) {
			TypeHierarchy hierarchy = hierarchyFactory.apply(source);
			DeclaredAnnotations[] result = new DeclaredAnnotations[hierarchy.size()];
			int i = 0;
			boolean ignorable = true;
			for (Class<?> type : hierarchy) {
				type.getDeclaredAnnotations();
			}
			if (ignorable) {
				return null;
			}
			List<DeclaredAnnotations> result = null;
			for (Class<?> type : apply) {
				Annotation[] annotations = type.getDeclaredAnnotations();
				if (!isIgnorable(annotations)) {

				}
				result.add(getDeclaredAnnotations(type));
			}
			return result;
		}

	}

	private static class MethodAnnotationsScanner extends AnnotationsScanner {

		private static final Map<Class<?>, Method[]> methodsCache = new ConcurrentReferenceHashMap<>(
				256);

		static Results getResults(Method source, SearchStrategy searchStrategy) {
			return null;
		}
	}

	private static class ElementAnnotationsScanner extends AnnotationsScanner {

		private static Results getResults(AnnotatedElement source,
				SearchStrategy searchStrategy) {
			return null;
		}

	}

	/**
	 * Provides ordered access to the superclass and interface hierarchy of a given class.
	 */
	private static final class TypeHierarchy implements Iterable<Class<?>> {

		private static final Map<Class<?>, TypeHierarchy> superclassesCache = new ConcurrentReferenceHashMap<>();

		private static final Map<Class<?>, TypeHierarchy> superclassesAndInterfacesCache = new ConcurrentReferenceHashMap<>();

		private final Set<Class<?>> hierarchy = new LinkedHashSet<>();

		private TypeHierarchy(Class<?> type, boolean includeInterfaces) {
			collect(type, includeInterfaces);
		}

		private void collect(Class<?> type, boolean includeInterfaces) {
			if (type == null || Object.class.equals(type)) {
				return;
			}
			this.hierarchy.add(type);
			collect(type.getSuperclass(), includeInterfaces);
			if (includeInterfaces) {
				for (Class<?> interfaceType : type.getInterfaces()) {
					collect(interfaceType, includeInterfaces);
				}
			}
		}

		@Override
		public Iterator<Class<?>> iterator() {
			return this.hierarchy.iterator();
		}

		public int size() {
			return this.hierarchy.size();
		}

		public static TypeHierarchy superclasses(Class<?> type) {
			return superclassesCache.computeIfAbsent(type,
					key -> new TypeHierarchy(type, false));
		}

		public static TypeHierarchy superclassesAndInterfaces(Class<?> type) {
			return superclassesAndInterfacesCache.computeIfAbsent(type,
					key -> new TypeHierarchy(type, true));
		}

	}

	private static class Results extends AbstractCollection<DeclaredAnnotations> {

		private static final Results NONE = new Results();

		@Override
		public Iterator<DeclaredAnnotations> iterator() {
			return null;
		}

		/**
		 * @param aggregates
		 * @return
		 */
		public static Results of(List<DeclaredAnnotations> aggregates) {
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("Auto-generated method stub");
		}

		@Override
		public int size() {
			return 0;
		}

		private static boolean isIgnorable(Annotation[] annotations) {
			for (Annotation annotation : annotations) {
				if (!isIgnorable(annotation.annotationType())) {
					return false;
				}
			}
			return true;
		}

		private static boolean isIgnorable(Class<?> type) {
			return (type == Nullable.class || type == Deprecated.class);
		}

		public static Results of(DeclaredAnnotations from) {
			throw new UnsupportedOperationException("Auto-generated method stub");
		}

	}

}
