@(username: String)(implicit r: RequestHeader)

$(function () {

    var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;
    var chatSocket = new WS("@routes.Application.chat(username).webSocketURL()");


    var sendMessage = function () {
        if(chatSocket.readyState === 1) {
            chatSocket.send(JSON.stringify(
                {text: $("#talk").val()}
            ));
            $("#talk").val('')
        }
    };

    var receiveEvent = function (event) {
        var data = JSON.parse(event.data);

        // Handle errors
        if (data.error) {
            chatSocket.close();
            $("#onError span").text(data.error);
            $("#onError").show();
            return
        } else {
            $("#onChat").show();
        }

        // Create the message element
        var el = $('<div class="message"><span></span><p></p></div>');
        $("span", el).text(data.user);
        $("p", el).text(data.message);
        $(el).addClass(data.kind);
        if (data.user.name == '@username') $(el).addClass('me');
        if(data.message !== "")$('#messages').append(el);
        $('#click').text("Click Me!" + data.count);

        // Update the members list
        $("#members").html('');
        $(data.members).each(function () {
            var li = document.createElement('li');
            li.textContent = this.name + ":" + this.count;
            if(this.name == '@username') {
                li.style.color = "red";
            }
            $("#members").append(li);
        })
    };

    var handleReturnKey = function (e) {
        if (e.charCode == 13 || e.keyCode == 13) {
            e.preventDefault();
            sendMessage()
        }
    };

    $("#talk").keypress(handleReturnKey);
    $("#click").click(sendMessage);

    chatSocket.onmessage = receiveEvent

});