# Tigase Meet Protocol
  
Protocol defined within this document will use `tigase:meet:0` namespace.

## 1. Determining support
If an entity supports Tigase Meet, it should advertise that fact by returning a feature of `tigase:meet:0` in response to the [XEP-0030: Service Discovery](https://xmpp.org/extensions/xep-0030.html) information request.

Additionally entity should advertise media types for which it supports group calls (ie. audio, video) as a feature composed of `tigase:meet:0:media:` and media name, ie. `audio`.

Example 1. Service discovery information request
````xml
<iq from='user1@example.com/res-1'
    id='ku6e51v3'
    to='meet.example.com'
    type='get'>
  <query xmlns='http://jabber.org/protocol/disco#info'/>
</iq>
````

Example 2. Service discovery information response
````xml
<iq from='meet.example.com'
    id='ku6e51v3'
    to='user1@example.com/res-1'
    type='result'>
  <query xmlns='http://jabber.org/protocol/disco#info'>
    <feature var='tigase:meet:0'/>
    <feature var='tigase:meet:0:media:audio'/>
    <feature var='tigase:meet:0:media:video'/>
  </query>
</iq>
````


## 2. How it work
### 2.1. Existing group call (MIX or MUC)
If MUC room or MIX channel are advertising support for `tigase:meet:0` it must act as a video group chat. That means, that room or channel bare JID will be handling/forwarding (accepting and answering) Jingle session requests required to establish a group call.

In this case, client wanting to join the group call, will not create a group call, but will use a room or channel bare JID to initiate a call as specified in [Joining the call](#joining-the-call)

### 2.2. Ad-hoc group call (many 1-1 client)
If client wants to create an ad-hoc group call, it needs to find a component supporting `tigase:meet:0` feature and proceed with [Creating a group call](#creating-a-group-call), [Joining the call](#joining-the-call) and then [Inviting participants to the call](#inviting-participants-to-the-call).

## 3. Use cases
### 3.1. Managing the call

#### 3.1.1 Creating a group call
Clients sends `<iq/>` stanza of type `set` to the bare JID of the video conferencing component with a `<create/>` element qualified by the `tigase:meet:0` namespace. 
`<create/>` element must contain one `<participant/>` element for each participant which should be invited to the newly crated group call and `jid` attribute of the element should contain bare JID of the participant. `<create/>` element.
Additionally, `<create/>` element should contain `<media/>` elements with `type` attribute set to the media type name which are allowed in the group call, ie. `audio` or `video`.

````xml
<iq type='set' id='create-1' to='meet.example.com'>
	<create xmlns='tigase:meet:0'>
        <media type="audio" />
        <media type="video"/>
		<participant>user2@example.com</participant>
		<participant>user3@example.com</participant>
  </create>
</iq>
````

Component will respond with `<iq/>` of type `result` and `<create/>` element qualified by `tigase:meet:0` namespace which will contain an `id` attribute with which value will be a localpart of the JID of newly created ad-hoc call. Domain of this JID will match domain of the component JID.

````xml
<iq type='result' id='create-1' from='meet.example.com'>
	<create id='23423' xmlns='tigase:meet:0'/>
</iq>
````

### 3.2. Establishing call

#### 3.2.1. Joining the call
Client initiates jingle call with the bare JID of the group call as it would during 1-1 Jingle session establishment.

In the response, component will not only establish this session, but will also initiate a Jingle session to the client. Client should accept this incoming session automatically (it will be coming from the same group chat bare JID). This incoming session initiation may be delayed until at least one other participant will join the group call.

#### 3.2.2. Inviting participants to the call
Client sends `<message/>` to each participant which should join the call. Message must contain `<propose/>` element qualified by `tigase:meet:0` namespace with `id` attribute used for identification of invitation.
`<propose/>` element must contain `<media/>` elements for each media type supported in the group call.

````xml
<message to='user2@example.com'>
	<propose xmlns='tigase:meet:0' id='a73sjjvkla37jfea' jid='23423@meet.example.com'>
        <media type="audio"/>
        <media type="video"/>
    </propose>
</message>
````

#### 3.2.3. Accepting invitation
Client will respond with a `<message/>` of the same type as invitation with `<accept/>` element qualified by `tigase:meet:0` namespace and with `id`attribute equal id of the invitation.
````xml
<message to='user2@example.com'>
	<accept xmlns='tigase:meet:0' id='a73sjjvkla37jfea' />
</message>
````
and will begin Jingle session establishment as specified in [Joining the call](#joining-the-call). If session establishment will fail for any reason, client should [Notify invitation sender of a failure](#notify-invitation-sender-of-a-failure).

#### 3.2.4. Notify invitation sender of a failure
Client will send a `<message/>` of the same type as invitation with `<failure/>` element qualified by `tigase:meet:0` namespace and `id` attribute equal id of the invitation.
````xml
<message to='user2@example.com'>
	<failure xmlns='tigase:meet:0' id='a73sjjvkla37jfea' />
</message>
````

#### 3.2.5. Rejecting invitation
Client will respond with a `<message/>` of the same type as invitation with `<reject/>` element qualified by  `tigase:meet:0` namespace and with `id`attribute equal id of the invitation.
````xml
<message to='user2@example.com'>
	<reject xmlns='tigase:meet:0' id='a73sjjvkla37jfea' />
</message>
````

**NOTE:** Clients should listen for those message-based invitations using carbons as they do for Jingle Message Initiation and act in the same way, so that accepting a call on a single client would silence other clients of the same user.

## 4. TODOs

1. ~~What if we want to invite people from the room/channel to that call? Should we send an invite? or just a message to the room/channel?~~ To invite participants in the call we would send a message to the channel.
2. ~~Should we kick out people, when they are kicked out from the channel/room? or i other cases as well?~~ People should be kicked out of the group call when they leave the channel.
3. We should consider a feature of manual "destruction" of the ad-hoc group call.
4. We should consider to allow inviting or kicking out people from the call.
5. It would be useful to add "metadata" exchange, so clients would know which A/V stream is sent by which participant (which bare JID).
6. Verify that usage of `tigase:meet:0:media:audio` is a good idea for versioned features.

