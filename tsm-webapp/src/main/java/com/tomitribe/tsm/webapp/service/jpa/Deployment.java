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
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

@Getter
@Setter
@Entity
@NamedQueries({
    @NamedQuery(name = "Deployment.findAll", query = "select d from Deployment d")
})
@Table(name = "tsm_deployment")
public class Deployment {
    @Id
    @GeneratedValue
    private long id;

    private String application;
    private String revision;

    @Lob
    private String output; // logs captured from crest environment

    @Temporal(TemporalType.TIMESTAMP)
    private Date created;

    @ManyToOne
    private Repository repository;

    @PrePersist
    private void prePersist() {
        setCreated(new Date());
    }
}
