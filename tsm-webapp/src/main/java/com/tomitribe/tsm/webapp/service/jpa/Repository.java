/*
 * Tomitribe Confidential
 *
 * Copyright Tomitribe Corporation. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package com.tomitribe.tsm.webapp.service.jpa;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

@Getter
@Setter
@Entity
@NamedQueries({
    @NamedQuery(name = "Repository.findAll", query = "select r from Repository r")
})
@Table(name = "tsm_repository")
public class Repository {
    @Id
    @GeneratedValue
    private long id;

    private String name;
    private String base;

    @Lob
    private String key;

    @Temporal(TemporalType.TIMESTAMP)
    private Date created;

    @Temporal(TemporalType.TIMESTAMP)
    private Date updated;

    @PrePersist
    private void prePersist() {
        final Date now = new Date();
        setCreated(now);
        setUpdated(now);
    }

    @PreUpdate
    private void preUpdate() {
        setUpdated(new Date());
    }

    @Override
    public boolean equals(final Object o) {
        return this == o || !(o == null || !Repository.class.isInstance(o)) && getId() == Repository.class.cast(o).getId();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
