// Rehabit — entrada de página e stagger de listas (Software/dashboard), via GSAP.
// Carregar depois de script.js e antes de pages/*.js.
(function () {
  function prefersReducedMotion() {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  // Exposto para pages/*.js chamarem depois de popular uma lista/tabela dinâmica.
  window.RehabitAnim = {
    staggerList: function (container) {
      if (!container || typeof gsap === "undefined" || prefersReducedMotion()) return;
      var items = container.children;
      if (!items || !items.length) return;
      gsap.from(items, { opacity: 0, y: 10, duration: 0.35, stagger: 0.05, ease: "power2.out" });
    },
  };

  // O <script> do GSAP virou "async" para que um CDN que não responde não
  // segure os scripts da própria página (era o que deixava a Home em branco:
  // sem indicadores, sem pacientes e sem busca). O preço é que ele pode
  // chegar depois daqui — esperamos um instante por ele e, se não vier, a
  // tela entra sem animação. Esperar mais seria pior: o gsap.from() esconde
  // o que já está desenhado, e o conteúdo piscaria antes de reaparecer.
  document.addEventListener("DOMContentLoaded", function () {
    if (prefersReducedMotion()) return;
    aoTerBiblioteca(
      "gsap",
      function (chegou) {
        if (chegou) animarEntrada();
      },
      500
    );
  });

  function animarEntrada() {
    var sidebar = document.querySelector(".sidebar");
    var mobileTopbar = document.querySelector(".mobile-topbar");
    var mobileBottomnav = document.querySelector(".mobile-bottomnav");
    var header = document.querySelector(".main > header");
    var cards = document.querySelectorAll(".main .card");
    var fab = document.querySelector(".fab");

    var tl = gsap.timeline({ defaults: { ease: "power2.out" } });

    if (sidebar) tl.from(sidebar, { opacity: 0, x: -16, duration: 0.4 });
    if (mobileTopbar) tl.from(mobileTopbar, { opacity: 0, y: -10, duration: 0.35 }, "<");
    if (header) tl.from(header, { opacity: 0, y: 12, duration: 0.4 }, "-=0.15");
    if (cards.length) tl.from(cards, { opacity: 0, y: 16, duration: 0.4, stagger: 0.08 }, "-=0.15");
    if (mobileBottomnav) tl.from(mobileBottomnav, { opacity: 0, y: 16, duration: 0.35 }, "-=0.3");
    if (fab) tl.from(fab, { opacity: 0, scale: 0.8, duration: 0.35 }, "-=0.1");
  }
})();
