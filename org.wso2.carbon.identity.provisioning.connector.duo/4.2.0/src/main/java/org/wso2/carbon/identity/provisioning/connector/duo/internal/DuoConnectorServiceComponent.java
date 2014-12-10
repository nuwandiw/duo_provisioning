
package org.wso2.carbon.identity.provisioning.connector.duo.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.identity.provisioning.AbstractProvisioningConnectorFactory;
import org.wso2.carbon.identity.provisioning.connector.duo.DuoProvisioningConnectorFactory;

/**
* @scr.component name=
*                "org.wso2.carbon.identity.provisioning.connector.duo.internal.DuoConnectorServiceComponent"
*                immediate="true"
*/
public class DuoConnectorServiceComponent {

    private static Log log = LogFactory.getLog(DuoConnectorServiceComponent.class);

    protected void activate(ComponentContext context) {

        if (log.isDebugEnabled()) {
            log.debug("Activating DuoConnectorServiceComponent");
        }

        try {
            DuoProvisioningConnectorFactory duoProvisioningConnectorFactory = new DuoProvisioningConnectorFactory();

            context.getBundleContext().registerService(
                    AbstractProvisioningConnectorFactory.class.getName(),
                    duoProvisioningConnectorFactory, null);
            if (log.isDebugEnabled()) {
                log.debug("Duo Identity Provisioning Connector bundle is activated");
            }
        } catch (Throwable e) {
            log.fatal(" Error while activating Duo Identity Provisioning Connector ", e);
        }
    }
}
