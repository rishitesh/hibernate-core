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

import org.hibernate.mapping.PropertyGeneration;
import org.hibernate.metamodel.binding.state.SimpleAttributeBindingState;
import org.hibernate.metamodel.relational.state.ValueRelationalState;
import org.hibernate.metamodel.relational.state.ColumnRelationalState;

/**
 * TODO : javadoc
 *
 * @author Steve Ebersole
 */
public class SimpleAttributeBinding extends AbstractAttributeBinding implements KeyValueBinding {
	private final boolean forceNonNullable;
	private final boolean forceUnique;
	private boolean insertable;
	private boolean updateable;
	private boolean keyCasadeDeleteEnabled;
	private String unsavedValue;
	private PropertyGeneration generation;

	SimpleAttributeBinding(EntityBinding entityBinding, boolean forceNonNullable, boolean forceUnique) {
		super( entityBinding );
		this.forceNonNullable = forceNonNullable;
		this.forceUnique = forceUnique;
	}

	public final SimpleAttributeBinding initialize(SimpleAttributeBindingState state) {
		super.initialize( state );
		insertable = state.isInsertable();
		updateable = state.isUpdateable();
		keyCasadeDeleteEnabled = state.isKeyCascadeDeleteEnabled();
		unsavedValue = state.getUnsavedValue();
		generation = state.getPropertyGeneration() == null ? PropertyGeneration.NEVER : state.getPropertyGeneration();
		return this;
	}

	public SimpleAttributeBinding initialize(ValueRelationalState state) {
		super.initializeValueRelationalState( state );
		return this;
	}

	private boolean isUnique(ColumnRelationalState state) {
		return isPrimaryKey() || state.isUnique();
	}

	@Override
	public boolean isSimpleValue() {
		return true;
	}

	public boolean isInsertable() {
		return insertable;
	}

	protected void setInsertable(boolean insertable) {
		this.insertable = insertable;
	}

	public boolean isUpdateable() {
		return updateable;
	}

	protected void setUpdateable(boolean updateable) {
		this.updateable = updateable;
	}

	@Override
	public boolean isKeyCasadeDeleteEnabled() {
		return keyCasadeDeleteEnabled;
	}

	public void setKeyCasadeDeleteEnabled(boolean keyCasadeDeleteEnabled) {
		this.keyCasadeDeleteEnabled = keyCasadeDeleteEnabled;
	}

	@Override
	public String getUnsavedValue() {
		return unsavedValue;
	}

	public void setUnsavedValue(String unsaveValue) {
		this.unsavedValue = unsaveValue;
	}

	public boolean forceNonNullable() {
		return forceNonNullable;
	}

	public boolean forceUnique() {
		return forceUnique;
	}

	public PropertyGeneration getGeneration() {
		return generation;
	}

}
