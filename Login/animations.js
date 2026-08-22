// Rehabit — entrada de página (Login/Auth), via GSAP.
// Carregar depois de script.js e antes de modal.js.
(function () {
  function prefersReducedMotion() {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  document.addEventListener("DOMContentLoaded", function () {
    if (typeof gsap === "undefined" || prefersReducedMotion()) return;

    var photo = document.querySelector(".photo");
    var divider = document.querySelector(".divider");
    var logo = document.querySelector(".logo");
    var heading = document.querySelectorAll(".form-content > h1, .form-content > .subtitle");
    var fields = document.querySelectorAll(".fields .field");
    var rest = document.querySelectorAll(".form-content > .forgot-link, .form-content > .btn-primary, .form-content > .footer-link");

    var tl = gsap.timeline({ defaults: { ease: "power2.out" } });

    if (photo) tl.from(photo, { opacity: 0, x: -16, duration: 0.6 });
    if (divider) {
      var fromLeft = divider.classList.contains("left");
      tl.fromTo(
        divider,
        { clipPath: fromLeft ? "inset(0 100% 0 0)" : "inset(0 0 0 100%)" },
        { clipPath: "inset(0 0 0 0)", duration: 0.5 },
        "-=0.35"
      );
    }
    if (logo) tl.from(logo, { opacity: 0, scale: 0.85, duration: 0.4 }, "-=0.35");
    if (heading.length) tl.from(heading, { opacity: 0, y: 14, duration: 0.4, stagger: 0.08 }, "-=0.15");
    if (fields.length) tl.from(fields, { opacity: 0, y: 12, duration: 0.35, stagger: 0.06 }, "-=0.15");
    if (rest.length) tl.from(rest, { opacity: 0, y: 10, duration: 0.35, stagger: 0.06 }, "-=0.1");
  });
})();
