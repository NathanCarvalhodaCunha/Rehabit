// Rehabit — entrada pela conta de demonstração.
//
// O botão "Entrar na demonstração" da página inicial abre login.html?demo.
// Aqui os campos já chegam preenchidos com a Mariana, fisioterapeuta da
// Clínica Alvorada (fictícia, criada pela API com REHABIT_DEMO=true): o
// visitante só resolve a verificação anti-robô e clica em Entrar. A senha
// pode ficar pública porque a API deixa essa conta só de leitura
// (JwtAuthenticationFilter.ehDaDemonstracao); é a de REHABIT_DEMO_SENHA, não
// a das outras contas da Alvorada.
(function () {
  if (!new URLSearchParams(location.search).has("demo")) return;

  var email = document.getElementById("email");
  var senha = document.getElementById("password");
  var campos = document.querySelector("#loginForm .fields");
  if (!email || !senha || !campos) return;

  email.value = "mariana.bertolini@alvorada.example";
  senha.value = "Rehabit@2026";

  var aviso = document.createElement("p");
  aviso.className = "aviso-demo";
  aviso.innerHTML =
    "<strong>Conta de demonstração.</strong> Você entra como Mariana Costa Bertolini, " +
    "fisioterapeuta de uma clínica fictícia. Dá para ver tudo, mas nada pode ser alterado.";
  campos.parentNode.insertBefore(aviso, campos);
})();
