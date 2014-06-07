<#include "/page/layout/_layout.ftl"/>
<@layout activebar="index" html_title=i18n.getText("index.name")>
<style>
    * {font-family: tahoma; font-size: 12px; padding: 0px; margin: 0px;}
    p {line-height: 18px;}
    div {width: 500px; margin-left: auto; margin-right: auto;}
    #rooms {padding: 5px; background: #ffc0cb; border-radius: 5px; border: 1px solid #CCC; margin-top: 10px;}
    #content {padding: 5px; background: #ddd; border-radius: 5px; border: 1px solid #CCC; margin-top: 10px;}
    #users {padding: 5px; background: #ddd; border-radius: 5px; border: 1px solid #CCC; margin-top: 10px;}
    #header {padding: 5px; background: #f5deb3; border-radius: 5px; border: 1px solid #CCC; margin-top: 10px;}
    #info {padding: 5px; background: #f5deb3; border-radius: 5px; border: 1px solid #CCC; margin-top: 10px;}
    #input {border-radius: 2px; border: 1px solid #ccc; margin-top: 10px; padding: 5px; width: 400px;}
    #status {width: 88px; display: block; float: left; margin-top: 15px;}
</style>
<script type="text/javascript" src="<@resource.static/>/javascript/atmosphere-min.js"></script>
<script type="text/javascript">
    $(function () {
        "use strict";

        var header = $('#header');
        var rooms = $('#rooms');
        var content = $('#content');
        var users = $('#users');
        var input = $('#input');
        var status = $('#status');
        var myName = false;
        var author = null;
        var logged = false;
        var socket = atmosphere;
        var subSocket;
        var transport = 'websocket';
        var fallbackTransport = 'long-polling'
        var connected = false;
        var uuid = 0;

        header.html($('<h3>', { text: 'Atmosphere Chat. Default transport is ' + transport + ', fallback is ' + fallbackTransport }));
        status.text('Choose chatroom:');
        input.removeAttr('disabled').focus();

        input.keydown(function (e) {
            if (e.keyCode === 13) {
                var msg = $(this).val();

                $(this).val('');
                if (!connected) {
                    connected = true;
                    connect(msg);
                    return;
                }

                // First message received is always the author's name
                if (author == null) {
                    author = msg;
                }

                input.removeAttr('disabled').focus();
                // Private message
                if (msg.indexOf(":") !== -1) {
                    var a = msg.split(":")[0];
                    subSocket.push(atmosphere.util.stringifyJSON({ user: a, message: msg}));
                } else {
                    subSocket.push(atmosphere.util.stringifyJSON({ author: author, message: msg, uuid: uuid }));
                }

                if (myName === false) {
                    myName = msg;
                }
            }
        });

        function connect(chatroom) {
            // We are now ready to cut the request
            var request = { url: '/im/' + chatroom,
                contentType: "application/json",
                logLevel: 'debug',
                transport: transport,
                trackMessageLength: true,
                reconnectInterval: 5000,
                fallbackTransport: fallbackTransport};

            request.onOpen = function (response) {
                content.html($('<p>', { text: 'Atmosphere connected using ' + response.transport }));
                status.text('Choose name:');
                input.removeAttr('disabled').focus();
                transport = response.transport;
                uuid = response.request.uuid;
            };

            request.onReopen = function (response) {
                content.html($('<p>', { text: 'Atmosphere re-connected using ' + response.transport }));
                input.removeAttr('disabled').focus();
            };

            request.onMessage = function (response) {

                var message = response.responseBody;
                try {
                    var json = atmosphere.util.parseJSON(message);
                } catch (e) {
                    console.log('This doesn\'t look like a valid JSON: ', message);
                    return;
                }

                input.removeAttr('disabled').focus();
                if (json.rooms) {
                    rooms.html($('<h2>', { text: 'Current room: ' + chatroom}));

                    var r = 'Available rooms: ';
                    for (var i = 0; i < json.rooms.length; i++) {
                        r += json.rooms[i] + "  ";
                    }
                    rooms.append($('<h3>', { text: r }))
                }

                if (json.users) {
                    var r = 'Connected users: ';
                    for (var i = 0; i < json.users.length; i++) {
                        r += json.users[i] + "  ";
                    }
                    users.html($('<h3>', { text: r }))
                }

                if (json.author) {
                    if (!logged && myName) {
                        logged = true;
                        status.text(myName + ': ').css('color', 'blue');
                    } else {
                        var me = json.author == author;
                        var date = typeof(json.time) == 'string' ? parseInt(json.time) : json.time;
                        addMessage(json.author, json.message, me ? 'blue' : 'black', new Date(date));
                    }
                }
            };

            request.onClose = function (response) {
                subSocket.push(atmosphere.util.stringifyJSON({ author: author, message: 'disconnecting' }));
            };

            request.onError = function (response) {
                content.html($('<p>', { text: 'Sorry, but there\'s some problem with your '
                        + 'socket or the server is down' }));
                logged = false;
            };

            request.onReconnect = function (request, response) {
                content.html($('<p>', { text: 'Connection lost, trying to reconnect. Trying to reconnect ' + request.reconnectInterval}));
                input.attr('disabled', 'disabled');
            };

            subSocket = socket.subscribe(request);
        }

        function addMessage(author, message, color, datetime) {
            content.append('<p><span style="color:' + color + '">' + author + '</span> @ ' + +(datetime.getHours() < 10 ? '0' + datetime.getHours() : datetime.getHours()) + ':'
                    + (datetime.getMinutes() < 10 ? '0' + datetime.getMinutes() : datetime.getMinutes())
                    + ': ' + message + '</p>');
        }
    });

</script>
<div id="header"><h3>Atmosphere Chat. Default transport is WebSocket, fallback is long-polling</h3></div>
<div id="rooms"></div>
<div id="users"></div>
<div id="content"></div>
<div id="info"><h4>To send a private message, type the name followed by : and then the message. To send a message to all chatroon, type all: message</h4></div>
<div>
    <span id="status">Connecting...</span>
    <input type="text" id="input" disabled="disabled"/>
</div>
</@layout>