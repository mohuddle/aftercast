# :net:common

This module contains general network related utilities.
Provides the shared OkHttp client, user-agent configuration, and HTTP helpers used by all other net modules.
`UserAgentInterceptor` sends `Aftercast/<version>`. `ClientConfigurator` sets that value at startup. Podcast Index search uses the same header.
