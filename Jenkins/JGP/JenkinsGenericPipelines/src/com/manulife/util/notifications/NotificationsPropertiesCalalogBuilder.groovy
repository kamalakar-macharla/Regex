package com.manulife.util.notifications

import com.manulife.util.propertyfile.PropertiesCatalog

/**
 *
 * Responsible to populate the properties catalog with the properties required by the NotificationSender class.
 *
 **/
class NotificationsPropertiesCalalogBuilder {
    static build(PropertiesCatalog propertiesCatalog) {
        propertiesCatalog.addOptionalProperty('emailJenkinsNotificationsTo', 'Defaulting emailJenkinsNotificationsTo property to null', null)
        propertiesCatalog.addOptionalProperty('slackChannel', 'Defaulting slackChannel property to null', null)
        propertiesCatalog.addOptionalProperty('slackTokenCredentialID', 'Defaulting slackTokenCredentialID property to null', null)
    }
}
