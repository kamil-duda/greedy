$(() => {
    let uploadButton = $(".upload-button")
    let fileInput = $(".file-input")

    uploadButton.attr("disabled", true)

    fileInput.on("change", (file) => {
        let isFileChosen = fileInput.get(0).files.length === 1
        uploadButton.attr("disabled", !isFileChosen)
    })
});