package org.hibernate.envers.test.integration.jta;

import javax.persistence.EntityManager;
import java.util.Arrays;

import org.testng.annotations.Test;

import org.hibernate.ejb.Ejb3Configuration;
import org.hibernate.envers.test.AbstractEntityTest;
import org.hibernate.envers.test.entities.IntTestEntity;

import org.hibernate.testing.jta.TestingJtaBootstrap;

/**
 * Same as {@link org.hibernate.envers.test.integration.basic.Simple}, but in a JTA environment.
 * @author Adam Warski (adam at warski dot org)
 */
public class JtaTransaction extends AbstractEntityTest {
    private Integer id1;

    public void configure(Ejb3Configuration cfg) {
        cfg.addAnnotatedClass(IntTestEntity.class);

        addJTAConfig(cfg);
    }

    @Test
    public void initData() throws Exception {
        TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();

        newEntityManager();
        EntityManager em = getEntityManager();
        em.joinTransaction();
        IntTestEntity ite = new IntTestEntity(10);
        em.persist(ite);
        id1 = ite.getId();

        TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

        //

        TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();

        newEntityManager();
        em = getEntityManager();
        ite = em.find(IntTestEntity.class, id1);
        ite.setNumber(20);

        TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
    }

    @Test(dependsOnMethods = "initData")
    public void testRevisionsCounts() throws Exception {
        assert Arrays.asList(1, 2).equals(getAuditReader().getRevisions(IntTestEntity.class, id1)); 
    }

    @Test(dependsOnMethods = "initData")
    public void testHistoryOfId1() {
        IntTestEntity ver1 = new IntTestEntity(10, id1);
        IntTestEntity ver2 = new IntTestEntity(20, id1);

        assert getAuditReader().find(IntTestEntity.class, id1, 1).equals(ver1);
        assert getAuditReader().find(IntTestEntity.class, id1, 2).equals(ver2);
    }
}