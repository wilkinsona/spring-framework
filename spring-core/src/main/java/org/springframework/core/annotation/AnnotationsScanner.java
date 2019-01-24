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
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.commons.logging.Log;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.annotation.type.DeclaredAnnotations;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Scanner search for {@link DeclaredAnnotations} on the hierarchy of an
 * {@link AnnotatedElement}.
 *
 * @author Phillip Webb
 * @since 5.2
 */
class AnnotationsScanner implements Iterable<DeclaredAnnotations> {

	private static Map<AnnotatedElement, Results<?>> cache = new ConcurrentReferenceHashMap<>(
			256);

	@Nullable
	private static transient Log logger;

	private final Results<?> results;

	private final SearchStrategy searchStrategy;

	AnnotationsScanner(AnnotatedElement source, SearchStrategy searchStrategy) {
		this.results = cache.computeIfAbsent(source, Results::create);
		this.searchStrategy = searchStrategy;
	}

	@Override
	public Iterator<DeclaredAnnotations> iterator() {
		return this.results.get(this.searchStrategy).iterator();
	}

	public int size() {
		return this.results.get(this.searchStrategy).size();
	}

	static void clearCache() {
		cache.clear();
		TypeHierarchy.superclassesCache.clear();
		TypeHierarchy.superclassesAndInterfacesCache.clear();
		MethodResults.methodsCache.clear();
	}

	/**
	 * Cachable results for a single {@link AnnotatedElement} source.
	 *
	 * @param <E> the annotated element type
	 * @see ClassResults
	 * @see MethodResults
	 * @see ElementResults
	 */
	private static abstract class Results<E extends AnnotatedElement> {

		private final E source;

		private final Map<SearchStrategy, Collection<DeclaredAnnotations>> results = new ConcurrentHashMap<>();

		Results(E source) {
			this.source = source;
		}

		public final Collection<DeclaredAnnotations> get(SearchStrategy searchStrategy) {
			Collection<DeclaredAnnotations> result = this.results.get(searchStrategy);
			if (result == null) {
				result = compute(searchStrategy);
				this.results.put(searchStrategy, result);
			}
			return result;
		}

		protected abstract Collection<DeclaredAnnotations> compute(
				SearchStrategy searchStrategy);

		protected final E getSource() {
			return this.source;
		}

		static Results<?> create(AnnotatedElement element) {
			if (element instanceof Class<?>) {
				return new ClassResults((Class<?>) element);
			}
			if (element instanceof Method) {
				return new MethodResults((Method) element);
			}
			return new ElementResults(element);
		}

	}

	/**
	 * Cacheable results for a single class element.
	 */
	private static class ClassResults extends Results<Class<?>> {

		ClassResults(Class<?> source) {
			super(source);
		}

		@Override
		protected Collection<DeclaredAnnotations> compute(SearchStrategy searchStrategy) {
			switch (searchStrategy) {
				case DIRECT:
					return computeDirect();
				case INHERITED_ANNOTATIONS:
					return computeInheritedAnnotations();
				case SUPER_CLASS:
					return computeWithHierarchy(TypeHierarchy::superclasses);
				case EXHAUSTIVE:
					return computeWithHierarchy(TypeHierarchy::superclassesAndInterfaces);
			}
			throw new IllegalStateException(
					"Unsupported search strategy " + searchStrategy);
		}

		private Collection<DeclaredAnnotations> computeDirect() {
			return Collections.singleton(getDeclaredAnnotations(getSource()));
		}

		private Collection<DeclaredAnnotations> computeInheritedAnnotations() {
			List<DeclaredAnnotations> result = new ArrayList<>();
			Set<Class<?>> present = getAnnotationTypes(getSource().getAnnotations());
			for (Class<?> type : TypeHierarchy.superclasses(getSource())) {
				Set<Annotation> annotations = new LinkedHashSet<>(present.size());
				for (Annotation annotation : type.getDeclaredAnnotations()) {
					if(present.remove(annotation.annotationType())) {
						annotations.add(annotation);
					}
				}
				result.add(DeclaredAnnotations.from(type, annotations));
			}
			return result;
		}

		private Set<Class<?>> getAnnotationTypes(Annotation[] annotations) {
			Set<Class<?>> types = new HashSet<>(annotations.length);
			for (Annotation annotation : annotations) {
				types.add(annotation.annotationType());
			}
			return types;
		}

		private Collection<DeclaredAnnotations> computeWithHierarchy(
				Function<Class<?>, TypeHierarchy> hierarchyFactory) {
			List<DeclaredAnnotations> result = new ArrayList<>();
			for (Class<?> type : hierarchyFactory.apply(getSource())) {
				result.add(getDeclaredAnnotations(type));
			}
			return result;
		}

		private DeclaredAnnotations getDeclaredAnnotations(Class<?> type) {
			return DeclaredAnnotations.from(type, type.getDeclaredAnnotations());
		}

	}

	/**
	 * Cacheable results for a single method element.
	 */
	private static class MethodResults extends Results<Method> {

		private static final Method[] NO_METHODS = {};

		private static final Annotation[] NO_ANNOTATIONS = {};

		private static final Map<Class<?>, Method[]> methodsCache = new ConcurrentReferenceHashMap<>(256);

		MethodResults(Method source) {
			super(source);
		}

		@Override
		protected Collection<DeclaredAnnotations> compute(SearchStrategy searchStrategy) {
			switch (searchStrategy) {
				case DIRECT:
					return computeDirect();
				case INHERITED_ANNOTATIONS:
					return get(SearchStrategy.DIRECT);
				case SUPER_CLASS:
					return computeWithHierarchy(TypeHierarchy::superclasses);
				case EXHAUSTIVE:
					return computeWithHierarchy(TypeHierarchy::superclassesAndInterfaces);
			}
			throw new IllegalStateException(
					"Unsupported search strategy " + searchStrategy);
		}

		private Collection<DeclaredAnnotations> computeDirect() {
			return Collections.singleton(getAnnotations(getSource()));
		}

		private Collection<DeclaredAnnotations> computeWithHierarchy(
				Function<Class<?>, TypeHierarchy> hierarchyFactory) {
			Method source = getSource();
			if (Modifier.isPrivate(source.getModifiers())) {
				return get(SearchStrategy.DIRECT);
			}
			List<DeclaredAnnotations> result = new ArrayList<>();
			Class<?> declaringClass = source.getDeclaringClass();
			result.add(getAnnotations(source));
			for (Class<?> type : hierarchyFactory.apply(declaringClass)) {
				if (type != declaringClass) {
					for (Method method : getMethods(type)) {
						if (isOverride(type, method)) {
							result.add(getAnnotations(method));
						}
					}
				}
			}
			return result;
		}

		private DeclaredAnnotations getAnnotations(Method method) {
			Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
			Annotation[] methodAnnotations = method.getDeclaredAnnotations();
			Annotation[] bridgedMethodAnnotations = bridgedMethod != method
					? bridgedMethod.getDeclaredAnnotations()
					: NO_ANNOTATIONS;
			if(hasAnnotations(methodAnnotations) || hasAnnotations(bridgedMethodAnnotations)) {
				Set<Annotation> annotations = new LinkedHashSet<>(methodAnnotations.length + bridgedMethodAnnotations.length);
				for (Annotation annotation : methodAnnotations) {
					annotations.add(annotation);
				}
				for (Annotation annotation : bridgedMethodAnnotations) {
					annotations.add(annotation);
				}
				return DeclaredAnnotations.from(method, annotations);
			}
			return DeclaredAnnotations.NONE;
		}

		private boolean hasAnnotations(Annotation[] annotations) {
			for (Annotation annotation : annotations) {
				if(!isIgnorable(annotation.annotationType())) {
					return true;
				}
			}
			return false;
		}

		private boolean isIgnorable(Class<?> type) {
			return (type == Nullable.class || type == Deprecated.class);
		}


		private Method[] getMethods(Class<?> type) {
			if (type == Object.class) {
				return NO_METHODS;
			}
			if (type.isInterface() && ClassUtils.isJavaLanguageInterface(type)) {
				return NO_METHODS;
			}
			Method[] result = methodsCache.get(type);
			if (result == null) {
				result = type.isInterface() ? type.getMethods() : type.getDeclaredMethods();
				methodsCache.put(type, result.length == 0 ? NO_METHODS : result);
			}
			return result;
		}

		private boolean isOverride(Class<?> type, Method method) {
			if (!type.isInterface() && Modifier.isPrivate(method.getModifiers())) {
				return false;
			}
			if (!method.getName().equals(getSource().getName())) {
				return false;
			}
			return hasSameParameterTypes(method);
		}

		private boolean hasSameParameterTypes(Method method) {
			if (method.getParameterCount() != getSource().getParameterCount()) {
				return false;
			}
			Class<?>[] types = getSource().getParameterTypes();
			if (Arrays.equals(method.getParameterTypes(), types)) {
				return true;
			}
			Class<?> implementationClass = getSource().getDeclaringClass();
			for (int i = 0; i < types.length; i++) {
				Class<?> resolved = ResolvableType.forMethodParameter(method, i,
						implementationClass).resolve();
				if (types[i] != resolved) {
					return false;
				}
			}
			return true;
		}

	}

	/**
	 * Cacheable results for a single annotated element.
	 */
	private static class ElementResults extends Results<AnnotatedElement> {

		public ElementResults(AnnotatedElement source) {
			super(source);
		}

		@Override
		protected Collection<DeclaredAnnotations> compute(SearchStrategy searchStrategy) {
			if (searchStrategy != SearchStrategy.DIRECT) {
				return get(SearchStrategy.DIRECT);
			}
			return Collections.singleton(DeclaredAnnotations.from(getSource(),
					getSource().getDeclaredAnnotations()));
		}

	}

	/**
	 * Provides ordred access to the superclass and interface hierarchy of a
	 * given class.
	 */
	private static final class TypeHierarchy implements Iterable<Class<?>> {

		private static Map<Class<?>, TypeHierarchy> superclassesCache = new ConcurrentReferenceHashMap<>();

		private static Map<Class<?>, TypeHierarchy> superclassesAndInterfacesCache = new ConcurrentReferenceHashMap<>();

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

		public static TypeHierarchy superclasses(Class<?> type) {
			return superclassesCache.computeIfAbsent(type,
					key -> new TypeHierarchy(type, false));
		}

		public static TypeHierarchy superclassesAndInterfaces(Class<?> type) {
			return superclassesAndInterfacesCache.computeIfAbsent(type,
					key -> new TypeHierarchy(type, true));
		}

	}

}
