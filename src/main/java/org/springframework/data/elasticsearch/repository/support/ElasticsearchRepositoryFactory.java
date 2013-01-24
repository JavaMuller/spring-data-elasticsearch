/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.elasticsearch.repository.support;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.elasticsearch.repository.query.ElasticsearchPartQuery;
import org.springframework.data.elasticsearch.repository.query.ElasticsearchQueryMethod;
import org.springframework.data.elasticsearch.repository.query.ElasticsearchStringQuery;
import org.springframework.data.querydsl.QueryDslPredicateExecutor;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.lang.reflect.Method;

import static org.springframework.data.querydsl.QueryDslUtils.QUERY_DSL_PRESENT;

/**
 * Factory to create {@link ElasticsearchRepository}
 *
 */
public class ElasticsearchRepositoryFactory extends RepositoryFactorySupport {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchEntityInformationCreator entityInformationCreator;

    public ElasticsearchRepositoryFactory(ElasticsearchOperations elasticsearchOperations) {
        Assert.notNull(elasticsearchOperations);
        this.elasticsearchOperations = elasticsearchOperations;
        this.entityInformationCreator = new ElasticsearchEntityInformationCreatorImpl(elasticsearchOperations.getElasticsearchConverter()
                .getMappingContext());
    }

    @Override
    public <T, ID extends Serializable> ElasticsearchEntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
        return entityInformationCreator.getEntityInformation(domainClass);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object getTargetRepository(RepositoryMetadata metadata) {
        SimpleElasticsearchRepository repository = new SimpleElasticsearchRepository(getEntityInformation(metadata.getDomainType()), elasticsearchOperations);
        repository.setEntityClass(metadata.getDomainType());
        return repository;
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        if (isQueryDslRepository(metadata.getRepositoryInterface())) {
            throw new IllegalArgumentException("QueryDsl Support has not been implemented yet.");
        }
        return SimpleElasticsearchRepository.class;
    }

    private static boolean isQueryDslRepository(Class<?> repositoryInterface) {
        return QUERY_DSL_PRESENT && QueryDslPredicateExecutor.class.isAssignableFrom(repositoryInterface);
    }

    @Override
    protected QueryLookupStrategy getQueryLookupStrategy(QueryLookupStrategy.Key key) {
        return new ElasticsearchQueryLookupStrategy();
    }

    private class ElasticsearchQueryLookupStrategy implements QueryLookupStrategy {

        @Override
        public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata, NamedQueries namedQueries) {

            ElasticsearchQueryMethod queryMethod = new ElasticsearchQueryMethod(method, metadata, entityInformationCreator);
            String namedQueryName = queryMethod.getNamedQueryName();

            if (namedQueries.hasQuery(namedQueryName)) {
                String namedQuery = namedQueries.getQuery(namedQueryName);
                return new ElasticsearchStringQuery(queryMethod, elasticsearchOperations, namedQuery);
            }
            else if (queryMethod.hasAnnotatedQuery()) {
                return new ElasticsearchStringQuery(queryMethod, elasticsearchOperations, queryMethod.getAnnotatedQuery());
            }
            return new ElasticsearchPartQuery(queryMethod, elasticsearchOperations);
        }
    }

}
