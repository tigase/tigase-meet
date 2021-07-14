/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
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
