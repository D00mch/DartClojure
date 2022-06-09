(ns dumch.base64)

;; NB: These functions use deprecated js/escape and js/unescape functions
;; https://developer.mozilla.org/en-US/docs/Glossary/Base64

(defn utf8_to_b64
  "Returns `str` encoded to base64. Unicode safe."
  [str]
  (-> str
      js/encodeURIComponent
      js/unescape
      js/btoa))

(defn b64_to_utf8
  "Returns `b64` decoded from base64. Unicode safe."
  [b64]
  (-> b64
      js/atob
      js/escape
      js/decodeURIComponent))

(comment
  (utf8_to_b64 "a")
  (utf8_to_b64 "✓ à la mode")
  (b64_to_utf8 "4pyTIMOgIGxhIG1vZGU=")
  )