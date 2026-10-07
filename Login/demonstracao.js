// Rehabit — entrada pela conta de demonstração.
//
// O botão "Entrar na demonstração" da página inicial abre login.html?demo.
// Aqui os campos já chegam preenchidos com uma profissional da Clínica
// Movimento (fictícia, criada pela API com REHABIT_DEMO=true): o visitante só
// resolve a verificação anti-robô e clica em Entrar. A senha pode ficar
// pública porque a API deixa essa conta só de leitura
// (JwtAuthenticationFilter.ehDaDemonstracao).
(function () {
  if (!new URLSearchParams(location.search).has("demo")) return;

  var email = document.getElementById("email");
  var senha = document.getElementById("password");
  var campos = document.querySelector("#loginForm .fields");
  if (!email || !senha || !campos) return;

  email.value = "ana.ribeiro@movimento.example";
  senha.value = "Rehabit@2026";

  var aviso = document.createElement("p");
  aviso.className = "aviso-demo";
  aviso.innerHTML =
    "<strong>Conta de demonstração.</strong> Você entra como Ana Paula Ribeiro, " +
    "fisioterapeuta de uma clínica fictícia. Dá para ver tudo, mas nada pode ser alterado.";
  campos.parentNode.insertBefore(aviso, campos);
})();
