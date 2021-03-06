/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010, Red Hat Inc. or third-party contributors as
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
package org.hibernate.metamodel.binding;

import org.hibernate.metamodel.MetadataSources;
import org.hibernate.metamodel.source.internal.MetadataImpl;
import org.hibernate.metamodel.source.spi.MetadataImplementor;

import static org.junit.Assert.assertEquals;

/**
 * Basic tests of {@code hbm.xml} binding code
 *
 * @author Steve Ebersole
 */
public class BasicHbmBindingTests extends AbstractBasicBindingTests {
	public EntityBinding buildSimpleEntityBinding() {
		return getEntityBinding(
				"org/hibernate/metamodel/binding/SimpleEntity.hbm.xml",
				SimpleEntity.class.getName()
		);
	}

	public EntityBinding buildSimpleVersionedEntityBinding() {
		return getEntityBinding(
				"org/hibernate/metamodel/binding/SimpleVersionedEntity.hbm.xml",
				SimpleVersionedEntity.class.getName()
		);
	}

	public MetadataImplementor buildMetadataWithManyToOne() {
		MetadataSources metadataSources = new MetadataSources(  basicServiceRegistry() );
		metadataSources.addResource( "org/hibernate/metamodel/binding/EntityWithManyToOne.hbm.xml" );
		metadataSources.addResource( "org/hibernate/metamodel/binding/SimpleEntity.hbm.xml" );
		assertEquals( 2, metadataSources.getJaxbRootList().size() );
		return ( MetadataImplementor ) metadataSources.buildMetadata();
	}

	private EntityBinding getEntityBinding(String resourceName, String entityName ) {
		MetadataSources metadataSources = new MetadataSources(  basicServiceRegistry() );
		metadataSources.addResource( resourceName );
		assertEquals( 1, metadataSources.getJaxbRootList().size() );
		MetadataImpl metadata = ( MetadataImpl ) metadataSources.buildMetadata();
		return metadata.getEntityBinding( entityName );
	}
}
