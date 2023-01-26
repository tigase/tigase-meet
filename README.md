<p align="center">
  <a href="https://tigase.net/">
    <img
      alt="Tigase Meet Component"
      src="https://github.com/tigaseinc/website-assets/raw/master/tigase/images/tigase-logo.png?raw=true"
      width="300"
    />
  </a>
</p>

<p align="center">
  The Tigase Meet component for Tigase XMPP Server.
</p>

<p align="center">
  <img alt="Tigase Logo" src="https://github.com/tigaseinc/website-assets/raw/master/tigase/images/tigase-logo.png?raw=true" width="25"/>
  <img src="https://tc.tigase.net/app/rest/builds/buildType:(id:TigaseMeet_Build)/statusIcon" width="100"/>
</p>

# What it is

Tigase Meet Component is XMPP component for Tigase XMPP Server providing support for [Tigase Meet Protocol](docs/Protocol.md) which is a new protocol for group calling (SFU) using [XEP-0166: Jingle](https://xmpp.org/extensions/xep-0166.html) and extensions of Jingle for establishing a call.
                                                                                                               
[Janus](https://janus.conf.meetecho.com/) media server is used internally as SFU. Tigase Meet requires `multistream` version of Janus to work properly.

# Features

It provides support to Tigase XMPP Server for group calling using [Janus](https://janus.conf.meetecho.com/).

# Support

When looking for support, please first search for answers to your question in the available online channels:

* Our online documentation: [Tigase Docs](https://docs.tigase.net)
* Our online forums: [Tigase Forums](https://help.tigase.net/portal/community)
* Our online Knowledge Base [Tigase KB](https://help.tigase.net/portal/kb)

If you didn't find an answer in the resources above, feel free to submit your question to either our
[community portal](https://help.tigase.net/portal/community) or open a [support ticket](https://help.tigase.net/portal/newticket).

# Downloads

You can download distribution version of Tigase XMPP Server which contains Tigase PubSub Component directly from [here](https://github.com/tigaseinc/tigase-server/releases).

If you wish to downloand SNAPSHOT build of the development version of Tigase XMPP Server which contains Tigase PubSub Component you can grab it from [here](https://build.tigase.net/nightlies/dists/latest/tigase-server-dist-max.zip).

# Installation and usage

Documentation of the project is part of the Tigase XMPP Server distribution package and it is also available as part of [Tigase XMPP Server documnetation page](https://docs.tigase.net/).

# Compilation

Compilation of the project is very easy as it is typical Maven project. All you need to do is to execute
````bash
mvn package test
````
to compile the project and run unit tests.

# License

<img alt="Tigase Tigase Logo" src="https://github.com/tigase/website-assets/blob/master/tigase/images/tigase-logo.png?raw=true" width="25"/> Official <a href="https://tigase.net/">Tigase</a> repository is available at: https://github.com/tigase/tigase-pubsub/.

Copyright (c) 2004 Tigase, Inc.

Licensed under AGPL License Version 3. Other licensing options available upon request.