// Opens scaled-down diagrams enlarged in a modal <dialog>. Links inside diagrams keep working.
;(function () {
  'use strict'

  var MEDIA = '.doc .imageblock img, .doc .imageblock svg'

  var dialog = document.createElement('dialog')
  dialog.className = 'image-zoom'
  dialog.setAttribute('closedby', 'any')
  document.body.appendChild(dialog)

  function naturalSize (media) {
    if (media instanceof SVGSVGElement) {
      var viewBox = media.viewBox.baseVal
      return { width: viewBox.width, height: viewBox.height }
    }
    return { width: media.naturalWidth, height: media.naturalHeight }
  }

  // Only scaled-down images get the zoom cursor; enlarging an image shown at full size is pointless
  function updateZoomable (media) {
    var block = media.closest('.imageblock')
    block.classList.toggle('is-zoomable', media.getBoundingClientRect().width < naturalSize(media).width)
  }

  function open (media) {
    var size = naturalSize(media)
    var copy = media.cloneNode(true)
    // The copy must not duplicate IDs of the page; references like url(#…) still resolve to the original
    copy.removeAttribute('id')
    copy.querySelectorAll('[id]').forEach(function (element) {
      element.removeAttribute('id')
    })
    copy.style.setProperty('--aspect', size.width / size.height)
    dialog.replaceChildren(copy)
    dialog.showModal()
  }

  var resizeObserver = new ResizeObserver(function (entries) {
    entries.forEach(function (entry) {
      updateZoomable(entry.target)
    })
  })
  document.querySelectorAll(MEDIA).forEach(function (media) {
    resizeObserver.observe(media)
  })

  document.addEventListener('click', function (event) {
    if (event.target.closest('a')) return
    var content = event.target.closest('.doc .imageblock.is-zoomable .content')
    if (content) open(content.querySelector('img, svg'))
  })

  dialog.addEventListener('click', function (event) {
    // A link closes the dialog and the browser follows it (e.g. to #QG1); a click on the backdrop
    // targets the dialog itself, which covers browsers without closedby support
    if (event.target === dialog || event.target.closest('a')) dialog.close()
  })
})()
