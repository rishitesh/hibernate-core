/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2007-2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.test.tm;

import javax.transaction.Transaction;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hibernate.ConnectionReleaseMode;
import org.hibernate.EntityMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.criterion.Order;
import org.hibernate.engine.transaction.internal.jta.CMTTransactionFactory;

import org.junit.Test;

import org.hibernate.testing.DialectChecks;
import org.hibernate.testing.RequiresDialectFeature;
import org.hibernate.testing.jta.TestingJtaBootstrap;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * @author Gavin King
 */
public class CMTTest extends BaseCoreFunctionalTestCase {
	@Override
	public String[] getMappings() {
		return new String[] { "tm/Item.hbm.xml" };
	}

	@Override
	public void configure(Configuration cfg) {
		TestingJtaBootstrap.prepare( cfg.getProperties() );
		cfg.setProperty( Environment.TRANSACTION_STRATEGY, CMTTransactionFactory.class.getName() );
		cfg.setProperty( Environment.AUTO_CLOSE_SESSION, "true" );
		cfg.setProperty( Environment.FLUSH_BEFORE_COMPLETION, "true" );
		cfg.setProperty( Environment.RELEASE_CONNECTIONS, ConnectionReleaseMode.AFTER_STATEMENT.toString() );
		cfg.setProperty( Environment.GENERATE_STATISTICS, "true" );
		cfg.setProperty( Environment.USE_QUERY_CACHE, "true" );
		cfg.setProperty( Environment.DEFAULT_ENTITY_MODE, EntityMode.MAP.toString() );
	}

	@Override
	public String getCacheConcurrencyStrategy() {
		return "transactional";
	}

	@Test
	public void testConcurrent() throws Exception {
		sessionFactory().getStatistics().clear();
		assertNotNull( sessionFactory().getEntityPersister( "Item" ).getCacheAccessStrategy() );
		assertEquals( 0, sessionFactory().getStatistics().getEntityLoadCount() );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s = openSession();
		Map foo = new HashMap();
		foo.put( "name", "Foo" );
		foo.put( "description", "a big foo" );
		s.persist( "Item", foo );
		Map bar = new HashMap();
		bar.put( "name", "Bar" );
		bar.put( "description", "a small bar" );
		s.persist( "Item", bar );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		sessionFactory().evictEntity( "Item" );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s1 = openSession();
		foo = ( Map ) s1.get( "Item", "Foo" );
		//foo.put("description", "a big red foo");
		//s1.flush();
		Transaction tx = TestingJtaBootstrap.INSTANCE.getTransactionManager().suspend();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s2 = openSession();
		foo = ( Map ) s2.get( "Item", "Foo" );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().resume( tx );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		sessionFactory().evictEntity( "Item" );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s1 = openSession();
		s1.createCriteria( "Item" ).list();
		//foo.put("description", "a big red foo");
		//s1.flush();
		tx = TestingJtaBootstrap.INSTANCE.getTransactionManager().suspend();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s2 = openSession();
		s2.createCriteria( "Item" ).list();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().resume( tx );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s2 = openSession();
		s2.createCriteria( "Item" ).list();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		assertEquals( 7, sessionFactory().getStatistics().getEntityLoadCount() );
		assertEquals( 0, sessionFactory().getStatistics().getEntityFetchCount() );
		assertEquals( 3, sessionFactory().getStatistics().getQueryExecutionCount() );
		assertEquals( 0, sessionFactory().getStatistics().getQueryCacheHitCount() );
		assertEquals( 0, sessionFactory().getStatistics().getQueryCacheMissCount() );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = openSession();
		s.createQuery( "delete from Item" ).executeUpdate();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
	}

	@Test
	public void testConcurrentCachedQueries() throws Exception {
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s = openSession();
		Map foo = new HashMap();
		foo.put( "name", "Foo" );
		foo.put( "description", "a big foo" );
		s.persist( "Item", foo );
		Map bar = new HashMap();
		bar.put( "name", "Bar" );
		bar.put( "description", "a small bar" );
		s.persist( "Item", bar );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		synchronized ( this ) {
			wait( 1000 );
		}

		sessionFactory().getStatistics().clear();

		sessionFactory().evictEntity( "Item" );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s4 = openSession();
		Transaction tx4 = TestingJtaBootstrap.INSTANCE.getTransactionManager().suspend();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s1 = openSession();
		List r1 = s1.createCriteria( "Item" ).addOrder( Order.asc( "description" ) )
				.setCacheable( true ).list();
		assertEquals( r1.size(), 2 );
		Transaction tx1 = TestingJtaBootstrap.INSTANCE.getTransactionManager().suspend();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s2 = openSession();
		List r2 = s2.createCriteria( "Item" ).addOrder( Order.asc( "description" ) )
				.setCacheable( true ).list();
		assertEquals( r2.size(), 2 );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheHitCount(), 2 );
		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheMissCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getEntityLoadCount(), 2 );
		assertEquals( sessionFactory().getStatistics().getEntityFetchCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getQueryExecutionCount(), 1 );
		assertEquals( sessionFactory().getStatistics().getQueryCachePutCount(), 1 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheHitCount(), 1 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheMissCount(), 1 );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().resume( tx1 );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s3 = openSession();
		s3.createCriteria( "Item" ).addOrder( Order.asc( "description" ) )
				.setCacheable( true ).list();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheHitCount(), 4 );
		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheMissCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getEntityLoadCount(), 2 );
		assertEquals( sessionFactory().getStatistics().getEntityFetchCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getQueryExecutionCount(), 1 );
		assertEquals( sessionFactory().getStatistics().getQueryCachePutCount(), 1 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheHitCount(), 2 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheMissCount(), 1 );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().resume( tx4 );
		List r4 = s4.createCriteria( "Item" ).addOrder( Order.asc( "description" ) )
				.setCacheable( true ).list();
		assertEquals( r4.size(), 2 );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheHitCount(), 6 );
		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheMissCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getEntityLoadCount(), 2 );
		assertEquals( sessionFactory().getStatistics().getEntityFetchCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getQueryExecutionCount(), 1 );
		assertEquals( sessionFactory().getStatistics().getQueryCachePutCount(), 1 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheHitCount(), 3 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheMissCount(), 1 );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = openSession();
		s.createQuery( "delete from Item" ).executeUpdate();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
	}

	@Test
	@RequiresDialectFeature(
			value = DialectChecks.DoesReadCommittedNotCauseWritersToBlockReadersCheck.class,
			comment = "write locks block readers"
	)
	public void testConcurrentCachedDirtyQueries() throws Exception {
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s = openSession();
		Map foo = new HashMap();
		foo.put( "name", "Foo" );
		foo.put( "description", "a big foo" );
		s.persist( "Item", foo );
		Map bar = new HashMap();
		bar.put( "name", "Bar" );
		bar.put( "description", "a small bar" );
		s.persist( "Item", bar );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		synchronized ( this ) {
			wait( 1000 );
		}

		sessionFactory().getStatistics().clear();

		sessionFactory().evictEntity( "Item" );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s4 = openSession();
		Transaction tx4 = TestingJtaBootstrap.INSTANCE.getTransactionManager().suspend();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s1 = openSession();
		List r1 = s1.createCriteria( "Item" ).addOrder( Order.asc( "description" ) )
				.setCacheable( true ).list();
		assertEquals( r1.size(), 2 );
		foo = ( Map ) r1.get( 0 );
		foo.put( "description", "a big red foo" );
		s1.flush();
		Transaction tx1 = TestingJtaBootstrap.INSTANCE.getTransactionManager().suspend();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s2 = openSession();
		List r2 = s2.createCriteria( "Item" ).addOrder( Order.asc( "description" ) )
				.setCacheable( true ).list();
		assertEquals( r2.size(), 2 );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheHitCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheMissCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getEntityLoadCount(), 4 );
		assertEquals( sessionFactory().getStatistics().getEntityFetchCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getQueryExecutionCount(), 2 );
		assertEquals( sessionFactory().getStatistics().getQueryCachePutCount(), 2 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheHitCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheMissCount(), 2 );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().resume( tx1 );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s3 = openSession();
		s3.createCriteria( "Item" ).addOrder( Order.asc( "description" ) )
				.setCacheable( true ).list();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheHitCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheMissCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getEntityLoadCount(), 6 );
		assertEquals( sessionFactory().getStatistics().getEntityFetchCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getQueryExecutionCount(), 3 );
		assertEquals( sessionFactory().getStatistics().getQueryCachePutCount(), 3 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheHitCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheMissCount(), 3 );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().resume( tx4 );
		List r4 = s4.createCriteria( "Item" ).addOrder( Order.asc( "description" ) )
				.setCacheable( true ).list();
		assertEquals( r4.size(), 2 );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheHitCount(), 2 );
		assertEquals( sessionFactory().getStatistics().getSecondLevelCacheMissCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getEntityLoadCount(), 6 );
		assertEquals( sessionFactory().getStatistics().getEntityFetchCount(), 0 );
		assertEquals( sessionFactory().getStatistics().getQueryExecutionCount(), 3 );
		assertEquals( sessionFactory().getStatistics().getQueryCachePutCount(), 3 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheHitCount(), 1 );
		assertEquals( sessionFactory().getStatistics().getQueryCacheMissCount(), 3 );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = openSession();
		s.createQuery( "delete from Item" ).executeUpdate();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
	}

	@Test
	public void testCMT() throws Exception {
		sessionFactory().getStatistics().clear();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s = openSession();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
		assertFalse( s.isOpen() );

		assertEquals( sessionFactory().getStatistics().getFlushCount(), 0 );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = openSession();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().rollback();
		assertFalse( s.isOpen() );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = openSession();
		Map item = new HashMap();
		item.put( "name", "The Item" );
		item.put( "description", "The only item we have" );
		s.persist( "Item", item );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
		assertFalse( s.isOpen() );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = openSession();
		item = ( Map ) s.createQuery( "from Item" ).uniqueResult();
		assertNotNull( item );
		s.delete( item );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
		assertFalse( s.isOpen() );

		assertEquals( sessionFactory().getStatistics().getTransactionCount(), 4 );
		assertEquals( sessionFactory().getStatistics().getSuccessfulTransactionCount(), 3 );
		assertEquals( sessionFactory().getStatistics().getEntityDeleteCount(), 1 );
		assertEquals( sessionFactory().getStatistics().getEntityInsertCount(), 1 );
		assertEquals( sessionFactory().getStatistics().getSessionOpenCount(), 4 );
		assertEquals( sessionFactory().getStatistics().getSessionCloseCount(), 4 );
		assertEquals( sessionFactory().getStatistics().getQueryExecutionCount(), 1 );
		assertEquals( sessionFactory().getStatistics().getFlushCount(), 2 );

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = openSession();
		s.createQuery( "delete from Item" ).executeUpdate();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
	}

	@Test
	public void testCurrentSession() throws Exception {
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s = sessionFactory().getCurrentSession();
		Session s2 = sessionFactory().getCurrentSession();
		assertSame( s, s2 );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
		assertFalse( s.isOpen() );

		// TODO : would be nice to automate-test that the SF internal map actually gets cleaned up
		//      i verified that is does currently in my debugger...
	}

	@Test
	public void testCurrentSessionWithIterate() throws Exception {
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s = openSession();
		Map item1 = new HashMap();
		item1.put( "name", "Item - 1" );
		item1.put( "description", "The first item" );
		s.persist( "Item", item1 );

		Map item2 = new HashMap();
		item2.put( "name", "Item - 2" );
		item2.put( "description", "The second item" );
		s.persist( "Item", item2 );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		// First, test iterating the partial iterator; iterate to past
		// the first, but not the second, item
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = sessionFactory().getCurrentSession();
		Iterator itr = s.createQuery( "from Item" ).iterate();
		if ( !itr.hasNext() ) {
			fail( "No results in iterator" );
		}
		itr.next();
		if ( !itr.hasNext() ) {
			fail( "Only one result in iterator" );
		}
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		// Next, iterate the entire result
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = sessionFactory().getCurrentSession();
		itr = s.createQuery( "from Item" ).iterate();
		if ( !itr.hasNext() ) {
			fail( "No results in iterator" );
		}
		while ( itr.hasNext() ) {
			itr.next();
		}
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = openSession();
		s.createQuery( "delete from Item" ).executeUpdate();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
	}

	@Test
	public void testCurrentSessionWithScroll() throws Exception {
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s = sessionFactory().getCurrentSession();
		Map item1 = new HashMap();
		item1.put( "name", "Item - 1" );
		item1.put( "description", "The first item" );
		s.persist( "Item", item1 );

		Map item2 = new HashMap();
		item2.put( "name", "Item - 2" );
		item2.put( "description", "The second item" );
		s.persist( "Item", item2 );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		// First, test partially scrolling the result with out closing
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = sessionFactory().getCurrentSession();
		ScrollableResults results = s.createQuery( "from Item" ).scroll();
		results.next();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		// Next, test partially scrolling the result with closing
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = sessionFactory().getCurrentSession();
		results = s.createQuery( "from Item" ).scroll();
		results.next();
		results.close();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		// Next, scroll the entire result (w/o closing)
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = sessionFactory().getCurrentSession();
		results = s.createQuery( "from Item" ).scroll();
		while ( results.next() ) {
			// do nothing
		}
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		// Next, scroll the entire result (closing)
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = sessionFactory().getCurrentSession();
		results = s.createQuery( "from Item" ).scroll();
		while ( results.next() ) {
			// do nothing
		}
		results.close();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		s = sessionFactory().getCurrentSession();
		s.createQuery( "delete from Item" ).executeUpdate();
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
	}

	@Test
	public void testAggressiveReleaseWithConnectionRetreival() throws Exception {
		TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
		Session s = openSession();
		Map item1 = new HashMap();
		item1.put( "name", "Item - 1" );
		item1.put( "description", "The first item" );
		s.save( "Item", item1 );

		Map item2 = new HashMap();
		item2.put( "name", "Item - 2" );
		item2.put( "description", "The second item" );
		s.save( "Item", item2 );
		TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();

		try {
			TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
			s = sessionFactory().getCurrentSession();
			s.createQuery( "from Item" ).scroll().next();
			s.connection();
			TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
		}
		finally {
			TestingJtaBootstrap.INSTANCE.getTransactionManager().begin();
			s = openSession();
			s.createQuery( "delete from Item" ).executeUpdate();
			TestingJtaBootstrap.INSTANCE.getTransactionManager().commit();
		}
	}

}
