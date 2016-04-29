/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.webapp.resource;

import com.tomitribe.tsm.webapp.resource.domain.SimpleRepository;
import com.tomitribe.tsm.webapp.service.jpa.Repository;
import com.tomitribe.web.jaxrs.JPACanJSResource;

import javax.ejb.ConcurrencyManagement;
import javax.ejb.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.Path;
import java.util.Date;

import static java.util.Optional.ofNullable;
import static javax.ejb.ConcurrencyManagementType.BEAN;

@Path("repository")
@Singleton
@ConcurrencyManagement(BEAN)
public class RepositoryResource extends JPACanJSResource<SimpleRepository, Repository> {
    @PersistenceContext
    private EntityManager em;

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    @Override
    protected Repository merge(final SimpleRepository view, final Repository jpa) {
        jpa.setBase(view.getBase());
        jpa.setName(view.getName());
        jpa.setCreated(view.getCreated());
        ofNullable(view.getKey()).ifPresent(jpa::setKey);
        // jpa.setUpdated(view.getUpdated()); // done by JPA
        return jpa;
    }

    @Override
    protected SimpleRepository toView(final Repository jpa) {
        final SimpleRepository repository = new SimpleRepository();
        repository.setId(jpa.getId());
        repository.setBase(jpa.getBase());
        repository.setName(jpa.getName());
        // repository.setKey(jpa.getKey()); // no key returned for security
        repository.setCreated(new Date(jpa.getCreated().getTime()));
        repository.setUpdated(new Date(jpa.getUpdated().getTime()));
        return repository;
    }
}
