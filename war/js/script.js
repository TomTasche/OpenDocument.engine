var onSuccess = function(data, result, xhr) {
  if (data.redirect) {
    window.location.href = data.redirect;
  } else {
    document.title += ' - ' + data['title'];
    document.body.innerHTML = data['content'];
  }
};

var onError = function(xhr, text_status, error) {
  if (xhr.status == 302) {
	  document.location.href = xhr.getResponseHeader('Location');
  }
};

var get = function(file_id) {
  $.ajax({
      url: '/svc?file_id=' + file_id,
      success: onSuccess,
      error: onError
  });
};