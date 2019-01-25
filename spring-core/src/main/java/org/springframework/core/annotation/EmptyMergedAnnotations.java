/*
 * Copyright 2002-2019 the original author or authors.
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
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Empty {@link MergedAnnotations} implementation.
 *
 * @author Phillip Webb
 * @since 5.2
 */
class EmptyMergedAnnotations extends AbstractMergedAnnotations {

	public static final EmptyMergedAnnotations INSTANCE = new EmptyMergedAnnotations();

	private EmptyMergedAnnotations() {
	}

	@Override
	public <A extends Annotation> MergedAnnotation<A> get(String annotationType,
			Predicate<? super MergedAnnotation<A>> predicate) {
		return MergedAnnotation.missing();
	}

	@Override
	public <A extends Annotation> Set<MergedAnnotation<A>> getAll(String annotationType) {
		return Collections.emptySet();
	}

	@Override
	public Set<MergedAnnotation<Annotation>> getAll() {
		return Collections.emptySet();
	}

}
