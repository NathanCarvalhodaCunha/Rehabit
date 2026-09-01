// Rehabit — entrada de página (Login/Auth), via GSAP.
// Carregar depois de script.js e antes de modal.js.
(function () {
  function prefersReducedMotion() {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  // O <script> do GSAP virou "async" para que um CDN que não responde não
  // segure os scripts da própria tela (era assim que a tela ficava sem
  // reagir a nada). O preço é que ele pode chegar depois daqui: esperamos um
  // instante e, se não vier, a tela entra sem animação. Esperar mais seria
  // pior — o gsap.from() esconde o que já está desenhado, e o conteúdo
  // piscaria antes de reaparecer.
  function quandoGsapChegar(aoChegar) {
    var limite = Date.now() + 500;
    (function tentar() {
      if (typeof gsap !== "undefined") return aoChegar();
      if (Date.now() > limite) return;
      setTimeout(tentar, 40);
    })();
  }

  document.addEventListener("DOMContentLoaded", function () {
    if (prefersReducedMotion()) return;
    quandoGsapChegar(animarEntrada);
  });

  function animarEntrada() {

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
  }
})();
