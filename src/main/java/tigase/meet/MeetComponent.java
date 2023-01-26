/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.meet;

import tigase.component.AbstractKernelBasedComponent;
import tigase.component.modules.impl.DiscoveryModule;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.selector.ClusterModeRequired;
import tigase.kernel.beans.selector.ConfigType;
import tigase.kernel.beans.selector.ConfigTypeEnum;
import tigase.kernel.core.Kernel;

@Bean(name = "meet", parent = Kernel.class, active = false)
@ConfigType(ConfigTypeEnum.DefaultMode)
@ClusterModeRequired(active = false)
public class MeetComponent extends AbstractKernelBasedComponent {
	
	@Override
	public String getDiscoCategory() {
		return "conference";
	}

	@Override
	public String getDiscoCategoryType() {
		return "voip";
	}

	@Override
	public String getDiscoDescription() {
		return "Meet";
	}

	@Override
	public boolean isDiscoNonAdmin() {
		return true;
	}

	@Override
	public boolean isSubdomain() {
		return true;
	}

	@Override
	protected void registerModules(Kernel kernel) {
		kernel.registerBean(DiscoveryModule.class).exec();
	}
}
