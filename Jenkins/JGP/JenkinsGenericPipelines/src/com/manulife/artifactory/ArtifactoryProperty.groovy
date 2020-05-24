package com.manulife.artifactory

/**
 *
 * Helper class to help with the handling of properties on Artifacts in Artifactory.
 *
 **/
class ArtifactoryProperty {
    // When adding properties on a artifact in Artifactory we have to make sure the property value doesn't contain some "special" characters.
    // This method replaces the forbidden characters by valid ones.
    // https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-ItemProperties

    // This file talks about interpreting parentheses in the URL path as
    // "capture groups" for wildcard patterns.
    // https://github.com/jfrog/build-info/blob/build-info-npm-extractor-2.13.13/build-info-extractor/src/main/java/org/jfrog/build/extractor/clientConfiguration/util/spec/SpecsHelper.java#L215

    // public void setProperties(String urlPath, String props) throws IOException {
    //     String url = ArtifactoryHttpClient.encodeUrl(urlPath + "?properties=" + props);
    //     PreemptiveHttpClient client = httpClient.getHttpClient();
    //     HttpPut httpPut = new HttpPut(url);
    //     checkNoContent(client.execute(httpPut), "Failed to set properties to '" + urlPath + "'");
    // }
    // https://github.com/jfrog/build-info/blob/build-info-maven3-plugin-2.7.0/build-info-extractor/src/main/java/org/jfrog/
    //                                                                                                        build/extractor/clientConfiguration/client/ArtifactoryDependenciesClient.java#L213

    // The following characters appear missing from the allowed ones in URI.allowed_query:
    //      /[]"
    // https://github.com/jfrog/build-info/blob/master/build-info-client/src/main/java/org/jfrog/build/util/URI.java

    // A backslash \ is missing too, but I did not have time to test it.

    // Even the latest Apache HTTP client does not allow raw double quotes (not sure why),
    // https://github.com/apache/httpcomponents-core/blob/rel/v5.0-beta9/httpcore5/src/main/java/org/apache/hc/core5/net/URLEncodedUtils.java

    static fixValue(String value) {
        if (value == null) {
            return 'null'
        }

        return value
            .replace('\\', '\\\\')
            .replace(',', '\\,')
            .replace('|', '\\|')
            .replace('=', '\\=')
            .replace('/', '_')
            .replace('[', '(')
            .replace(']', ')')
            .replace('"', '\'')
            .replaceAll('(?s)[\r\n]', '')
    }
}
