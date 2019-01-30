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
import java.lang.reflect.Member;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.core.annotation.type.DeclaredAnnotation;
import org.springframework.core.annotation.type.DeclaredAnnotations;
import org.springframework.core.annotation.type.DeclaredAttributes;
import org.springframework.core.annotation.type.StandardDeclaredAnnotation;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * {@link MergedAnnotations} implementation that uses
 * {@link AnnotationTypeMappings} to adapt annotations.
 *
 * @author Phillip Webb
 * @since 5.2
 */
final class TypeMappedAnnotations extends AbstractMergedAnnotations {

	private static FromElementResult lastFromElementResult = null;

	private final MappableAnnotations[] aggregates;

	private volatile Set<MergedAnnotation<Annotation>> all;

	private TypeMappedAnnotations(ClassLoader classLoader,
			List<DeclaredAnnotations> aggregates,
			RepeatableContainers repeatableContainers,
			AnnotationFilter annotationFilter) {
		this.aggregates = new MappableAnnotations[aggregates.size()];
		int aggregateIndex = 0;
		for (DeclaredAnnotations declaredAnnotations : aggregates) {
			this.aggregates[aggregateIndex] = new MappableAnnotations(classLoader,
					aggregateIndex, declaredAnnotations, repeatableContainers,
					annotationFilter);
			aggregateIndex++;
		}
	}

	@Override
	public <A extends Annotation> boolean isPresent(String annotationType) {
		for (MappableAnnotations annotations : this.aggregates) {
			if (annotations.isPresent(annotationType)) {
				return true;
			}
		}
		return false;
	}

	public <A extends Annotation> MergedAnnotation<A> get(String annotationType,
			@Nullable Predicate<? super MergedAnnotation<A>> predicate,
			MergedAnnotationSelector<A> selector) {
		selector = selector != null ? selector : MergedAnnotationSelectors.nearest();
		for (MappableAnnotations annotations : this.aggregates) {
			MergedAnnotation<A> result = annotations.get(annotationType, predicate,
					selector);
			if (result != null) {
				return result;
			}
		}
		return MergedAnnotation.missing();
	}

	public Set<MergedAnnotation<Annotation>> getAll() {
		Set<MergedAnnotation<Annotation>> all = this.all;
		if (all == null) {
			all = computeAll();
			this.all = all;
		}
		return all;
	}

	private Set<MergedAnnotation<Annotation>> computeAll() {
		Set<MergedAnnotation<Annotation>> result = new LinkedHashSet<>(totalSize());
		for (MappableAnnotations annotations : this.aggregates) {
			List<Deque<MergedAnnotation<Annotation>>> queues = new ArrayList<>(
					annotations.size());
			for (MappableAnnotation annotation : annotations) {
				queues.add(annotation.getQueue());
			}
			addAllInDepthOrder(result, queues);
		}
		return Collections.unmodifiableSet(result);
	}

	private void addAllInDepthOrder(Set<MergedAnnotation<Annotation>> result,
			List<Deque<MergedAnnotation<Annotation>>> queues) {
		int depth = 0;
		boolean hasMore = true;
		while (hasMore) {
			hasMore = false;
			for (Deque<MergedAnnotation<Annotation>> queue : queues) {
				hasMore = hasMore | addAllForDepth(result, queue, depth);
			}
			depth++;
		}
	}

	private boolean addAllForDepth(Set<MergedAnnotation<Annotation>> result,
			Deque<MergedAnnotation<Annotation>> queue, int depth) {
		while (!queue.isEmpty() && queue.peek().getDepth() <= depth) {
			result.add(queue.pop());
		}
		return !queue.isEmpty();
	}

	private int totalSize() {
		int size = 0;
		for (MappableAnnotations annotations : this.aggregates) {
			size += annotations.totalSize();
		}
		return size;
	}

	static MergedAnnotations from(AnnotatedElement element, SearchStrategy searchStrategy,
			RepeatableContainers repeatableContainers,
			AnnotationFilter annotationFilter) {
		Assert.notNull(repeatableContainers, "RepeatableContainers must not be null");
		Assert.notNull(annotationFilter, "AnnotationFilter must not be null");
		Assert.notNull(searchStrategy, "SearchStrategy must not be null");
		Assert.notNull(element, "Element must not be null");
		FromElementResult lastResult = lastFromElementResult;
		if (lastResult != null && lastResult.matches(element, searchStrategy,
				repeatableContainers, annotationFilter)) {
			return lastResult.getMergedAnnotations();
		}
		List<DeclaredAnnotations> aggregates = AnnotationsScanner.scan(element,
				searchStrategy);
		MergedAnnotations mergedAnnotations = of(null, aggregates, repeatableContainers,
				annotationFilter);
		lastFromElementResult = new FromElementResult(element, searchStrategy,
				repeatableContainers, annotationFilter, mergedAnnotations);
		return mergedAnnotations;
	}

	static MergedAnnotations from(@Nullable AnnotatedElement source,
			Annotation[] annotations, RepeatableContainers repeatableContainers,
			AnnotationFilter annotationFilter) {
		Assert.notNull(annotations, "Annotations must not be null");
		ClassLoader classLoader = getClassLoader(source);
		List<DeclaredAnnotations> aggregates = Collections.singletonList(
				DeclaredAnnotations.from(source, annotations));
		return of(classLoader, aggregates, repeatableContainers, annotationFilter);
	}

	private static ClassLoader getClassLoader(AnnotatedElement source) {
		if (source instanceof Member) {
			return getClassLoader(((Member) source).getDeclaringClass());
		}
		if (source instanceof Class) {
			return ((Class<?>) source).getClassLoader();
		}
		return null;
	}

	static MergedAnnotations of(ClassLoader classLoader,
			List<DeclaredAnnotations> aggregates,
			RepeatableContainers repeatableContainers,
			AnnotationFilter annotationFilter) {
		if (hasNoAnnotations(aggregates)) {
			return EmptyMergedAnnotations.INSTANCE;
		}
		return new TypeMappedAnnotations(classLoader, aggregates, repeatableContainers,
				annotationFilter);
	}

	private static boolean hasNoAnnotations(List<DeclaredAnnotations> aggregates) {
		for (DeclaredAnnotations annotations : aggregates) {
			if (annotations != DeclaredAnnotations.NONE) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A collection of {@link MappableAnnotation mappable annotations}.
	 */
	private static class MappableAnnotations implements Iterable<MappableAnnotation> {

		private final List<MappableAnnotation> mappableAnnotations;

		public MappableAnnotations(ClassLoader classLoader, int aggregateIndex,
				DeclaredAnnotations annotations,
				RepeatableContainers repeatableContainers,
				AnnotationFilter annotationFilter) {
			this.mappableAnnotations = new ArrayList<>(annotations.size());
			for (DeclaredAnnotation annotation : annotations) {
				ClassLoader annotationClassLoader = classLoader;
				if (classLoader == null
						&& annotation instanceof StandardDeclaredAnnotation) {
					annotationClassLoader = ((StandardDeclaredAnnotation) annotation).getAnnotation().getClass().getClassLoader();
				}
				add(annotationClassLoader, annotations.getSource(), aggregateIndex,
						annotation, repeatableContainers, annotationFilter);
			}
		}

		private void add(ClassLoader classLoader, Object source, int aggregateIndex,
				DeclaredAnnotation annotation, RepeatableContainers repeatableContainers,
				AnnotationFilter annotationFilter) {
			repeatableContainers.visit(annotation, classLoader, (type, attributes) -> {
				AnnotationTypeMappings mappings = AnnotationTypeMappings.forType(
						classLoader, repeatableContainers, annotationFilter, type);
				if (mappings != null) {
					this.mappableAnnotations.add(new MappableAnnotation(mappings, source,
							aggregateIndex, attributes));
				}
			});
		}

		public boolean isPresent(String annotationType) {
			for (MappableAnnotation mappableAnnotation : this.mappableAnnotations) {
				if (mappableAnnotation.isPresent(annotationType)) {
					return true;
				}
			}
			return false;
		}

		public <A extends Annotation> MergedAnnotation<A> get(String annotationType,
				@Nullable Predicate<? super MergedAnnotation<A>> predicate,
				MergedAnnotationSelector<A> selector) {
			MergedAnnotation<A> result = null;
			for (MappableAnnotation mappableAnnotation : this.mappableAnnotations) {
				MergedAnnotation<A> candidate = mappableAnnotation.get(annotationType,
						predicate);
				if (candidate != null && result == null) {
					result = candidate;
				}
				else if (candidate != null) {
					result = selector.select(result, candidate);
				}
			}
			return result;
		}

		public int size() {
			return this.mappableAnnotations.size();
		}

		public int totalSize() {
			int size = 0;
			for (MappableAnnotation mappableAnnotation : this.mappableAnnotations) {
				size += mappableAnnotation.size();
			}
			return size;
		}

		@Override
		public Iterator<MappableAnnotation> iterator() {
			return this.mappableAnnotations.iterator();
		}

	}

	/**
	 * A single mappable annotation.
	 */
	private static class MappableAnnotation {

		private final AnnotationTypeMappings mappings;

		private final Object source;

		private final int aggregateIndex;

		private final DeclaredAttributes attributes;

		public MappableAnnotation(AnnotationTypeMappings mappings, Object source,
				int aggregateIndex, DeclaredAttributes attributes) {
			this.mappings = mappings;
			this.source = source;
			this.aggregateIndex = aggregateIndex;
			this.attributes = attributes;
		}

		public boolean isPresent(String annotationType) {
			return this.mappings.get(annotationType) != null;
		}

		public <A extends Annotation> MergedAnnotation<A> get(String annotationType,
				@Nullable Predicate<? super MergedAnnotation<A>> predicate) {
			if (predicate == null) {
				AnnotationTypeMapping mapping = this.mappings.get(annotationType);
				return mapping != null ? map(mapping) : null;
			}
			for (AnnotationTypeMapping mapping : this.mappings.getAll()) {
				if (mapping.getAnnotationType().getClassName().equals(annotationType)) {
					MergedAnnotation<A> mapped = map(mapping);
					if (predicate.test(mapped)) {
						return mapped;
					}
				}
			}
			return null;
		}

		public Deque<MergedAnnotation<Annotation>> getQueue() {
			Deque<MergedAnnotation<Annotation>> queue = new ArrayDeque<>(size());
			for (AnnotationTypeMapping mapping : this.mappings.getAll()) {
				queue.add(map(mapping));
			}
			return queue;
		}

		private <A extends Annotation> TypeMappedAnnotation<A> map(
				AnnotationTypeMapping mapping) {
			return new TypeMappedAnnotation<A>(mapping, this.source, this.aggregateIndex,
					this.attributes);
		}

		public int size() {
			return this.mappings.getAll().size();
		}

	}

	private static class FromElementResult {

		private final AnnotatedElement element;

		private final SearchStrategy searchStrategy;

		private final RepeatableContainers repeatableContainers;

		private final AnnotationFilter annotationFilter;

		private final MergedAnnotations mergedAnnotations;

		FromElementResult(AnnotatedElement element, SearchStrategy searchStrategy,
				RepeatableContainers repeatableContainers,
				AnnotationFilter annotationFilter, MergedAnnotations mergedAnnotations) {
			this.element = element;
			this.searchStrategy = searchStrategy;
			this.repeatableContainers = repeatableContainers;
			this.annotationFilter = annotationFilter;
			this.mergedAnnotations = mergedAnnotations;
		}

		public boolean matches(AnnotatedElement element, SearchStrategy searchStrategy,
				RepeatableContainers repeatableContainers,
				AnnotationFilter annotationFilter) {
			return this.element == element && this.searchStrategy == searchStrategy
					&& this.repeatableContainers == repeatableContainers
					&& this.annotationFilter == annotationFilter;
		}

		public MergedAnnotations getMergedAnnotations() {
			return this.mergedAnnotations;
		}

	}

}
