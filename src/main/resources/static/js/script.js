console.log("this is script file")

const toggleSidebar=()=>{

    if($('.sidebar').is(":visible")) {
        
        //true  (we have to close)
        $(".sidebar").css("display","none")

        $(".content").css("margin-left","0%")
    }
    else{

        //false (we have to show)
        $(".sidebar").css("display","block")

        $(".content").css("margin-left","20%")
    }
}

const search=()=>{
    // console.log("searching....")

    let query = $("#search-input").val();

    if( query == ""){
        $(".search-result").hide();
    }
    else{
        //search
        console.log(query);

        //sending request to server

        let url = `http://localhost:8080/search/${query}`;

        fetch(url)
            .then(response=>{
                return response.json();
            }).then((data)=>{
                // data...
                // console.log(data);

                let text = `<div class="list-group">`

                data.forEach( (contact) => {
                    text += `<a href='/${contact.contactId}/contact' class="list-group-item list-list-group-item-action"> ${contact.name} </a>`
                });

                text += `</div>`;

                $(".search-result").html(text);
                $(".search-result").show();
            });
    }
};