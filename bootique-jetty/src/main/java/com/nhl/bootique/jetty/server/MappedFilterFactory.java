package com.nhl.bootique.jetty.server;

import static java.util.Arrays.asList;

import java.util.HashSet;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.annotation.WebFilter;

import com.nhl.bootique.jetty.MappedFilter;

/**
 * A factory that analyzes Filter annotations to create a {@link MappedFilter}
 * out of a {@link Filter}.
 * 
 * @since 0.14
 */
public class MappedFilterFactory {

	public MappedFilter toMappedFilter(Filter filter, int order) {

		WebFilter wfAnnotation = filter.getClass().getAnnotation(WebFilter.class);

		if (wfAnnotation == null) {
			throw new IllegalArgumentException(
					"Filter contains no @WebFilter annotation and can not be mapped directly. Wrap it in a MappedFilter instead.");
		}

		String name = wfAnnotation.filterName() != null && wfAnnotation.filterName().length() > 0
				? wfAnnotation.filterName() : null;
		Set<String> urlPatterns = new HashSet<>(asList(wfAnnotation.urlPatterns()));
		return new MappedFilter(filter, urlPatterns, name, order);
	}

}