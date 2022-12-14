function toHub(){
    location = "hub";
}

function createUser(){
    var email = $("#email").val();
    var userName = $("#username").val();
    var pw = $("#password").val();
    var errorStr = checkUserData(email, userName, pw);
    if(errorStr != "")
        return showConsole(errorStr);

    var data = {
        "email" : email,
        "userName" : userName,
        "pw" : pw
    }
    post("/user/createUser", data, createUserAfter, createUserError);
}

function createUserAfter(userDTO){
    waitThenGo(3);
}

function createUserError(xhr){
    showConsole("註冊失敗: " + xhr.result);
}

function checkUserData(email, userName, pw){
    var emailExp = /^[\w-\.]+@([\w-]+\.)+[\w-]+$/g;
    var pwExp = /^[\w-.@$!%*#?&]{6,}$/g;
    if(!emailExp.test(email))
        return "email格式錯誤";

    if(userName == "")
        return "用戶暱稱不可為空";

    if(!pwExp.test(pw))
        return "密碼未滿6位字母或格式錯誤";

    return "";
}

function showConsole(str){
    $(".error").text(str);
}

function waitThenGo(sec){
    if(sec < 0){
        location = "login";
        return;
    }

    showConsole(`註冊成功!! ${sec}秒後自動轉導...`);
    setInterval(function(){
            waitThenGo(sec - 1);
        }, 1000);
}