var onSuccess = function(data, result, xhr) {
	if (data.redirect) {
		window.location.href = data.redirect;
	} else {
		if (!data['content']) {
			data = $.parseJSON(data);
		}

		document.title += ' - ' + data['title'];
		document.body.innerHTML = data['content'];
	}
};

var onError = function(xhr, text_status, error) {
	document.body.innerText = "Failed to load this document, sorry.";
};

var get = function(file_id) {
	$.ajax({
		url : '/svc?file_id=' + file_id,
		success : onSuccess,
		error : onError
	});
};