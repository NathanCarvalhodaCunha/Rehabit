// Rehabit — área logada (Software)

const API_BASE_URL =
  location.protocol === "file:" || location.hostname === "localhost"
    ? "http://localhost:8080/api"
    : "https://rehabit-api-4tex.onrender.com/api";
window.API_BASE_URL = API_BASE_URL;

function getSessao() {
  try {
    return JSON.parse(localStorage.getItem("rehabit_usuario"));
  } catch (err) {
    return null;
  }
}

// Resolve o nome de um arquivo interno para a variante do tema atual
// (claro/escuro), para que a navegação nunca derrube um usuário do
// tema escuro de volta para uma tela clara.
function paginaTema(nomeBase) {
  return document.body.classList.contains("dark") ? `./${nomeBase}-escuro.html` : `./${nomeBase}.html`;
}

// Mesma ideia do paginaTema(), mas para o login, que fica fora de Software/.
function paginaLogin() {
  return document.body.classList.contains("dark") ? "../Login/login-escuro.html" : "../Login/login.html";
}

function cabecalhosAutenticados(extras) {
  const sessao = getSessao();
  const cabecalhos = Object.assign({}, extras || {});
  if (sessao && sessao.token) {
    cabecalhos["Authorization"] = `Bearer ${sessao.token}`;
  }
  return cabecalhos;
}

// fetch() rejeita com TypeError ("Failed to fetch") quando o servidor está
// fora do ar, sem internet ou o CORS barrou a resposta. Essa mensagem crua
// aparecia direto na tela para o usuário; aqui vira algo compreensível.
async function buscar(url, opcoes) {
  try {
    return await fetch(url, opcoes);
  } catch (err) {
    throw new Error("Não foi possível falar com o servidor. Verifique sua conexão e tente novamente.");
  }
}

async function tratarResposta(resposta, mensagemPadrao) {
  const dados = await resposta.json().catch(() => ({}));
  if (resposta.status === 401) {
    localStorage.removeItem("rehabit_usuario");
    window.location.href = paginaLogin();
    throw new Error("Sessão expirada.");
  }
  if (!resposta.ok) {
    throw new Error(dados.mensagem || mensagemPadrao);
  }
  return dados;
}

async function apiGet(caminho) {
  RehabitLoader.show();
  try {
    const resposta = await buscar(`${API_BASE_URL}${caminho}`, {
      headers: cabecalhosAutenticados(),
    });
    return await tratarResposta(resposta, "Não foi possível carregar os dados.");
  } finally {
    RehabitLoader.hide();
  }
}

async function apiPost(caminho, corpo) {
  RehabitLoader.show();
  try {
    const resposta = await buscar(`${API_BASE_URL}${caminho}`, {
      method: "POST",
      headers: cabecalhosAutenticados({ "Content-Type": "application/json" }),
      body: JSON.stringify(corpo),
    });
    return await tratarResposta(resposta, "Não foi possível salvar os dados.");
  } finally {
    RehabitLoader.hide();
  }
}

async function apiPut(caminho, corpo) {
  RehabitLoader.show();
  try {
    const resposta = await buscar(`${API_BASE_URL}${caminho}`, {
      method: "PUT",
      headers: cabecalhosAutenticados({ "Content-Type": "application/json" }),
      body: JSON.stringify(corpo),
    });
    return await tratarResposta(resposta, "Não foi possível salvar os dados.");
  } finally {
    RehabitLoader.hide();
  }
}

// Data de hoje por extenso em pt-BR, ex.: "Terça-feira, 25 de agosto",
// usada nos cabeçalhos de saudação da home (profissional/instituição).
function dataAtualPorExtenso() {
  const agora = new Date();
  const data = agora.toLocaleDateString("pt-BR", {
    weekday: "long",
    day: "2-digit",
    month: "long",
  });
  const hora = agora.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
  return data.charAt(0).toUpperCase() + data.slice(1) + " · " + hora;
}

async function apiDelete(caminho) {
  RehabitLoader.show();
  try {
    const resposta = await buscar(`${API_BASE_URL}${caminho}`, {
      method: "DELETE",
      headers: cabecalhosAutenticados(),
    });
    if (resposta.status === 204) return null;
    return await tratarResposta(resposta, "Não foi possível concluir a ação.");
  } finally {
    RehabitLoader.hide();
  }
}

function urlFoto(caminhoFoto) {
  if (!caminhoFoto) return null;
  if (caminhoFoto.startsWith("http")) return caminhoFoto;
  return API_BASE_URL.replace(/\/api$/, "") + caminhoFoto;
}

// Protege as páginas internas: sem sessão, volta para o login.
(function protegerPagina() {
  const sessao = getSessao();
  if (!sessao || !sessao.token) {
    window.location.href = paginaLogin();
  }
})();

// Se o navegador restaurar esta página do cache (voltar/avançar) sem
// re-executar os scripts, força um recarregamento. Sem isso, em um
// dispositivo compartilhado por vários profissionais, trocar de conta
// e navegar com o botão "voltar" podia reexibir em tela a lista de
// pacientes (ou outros dados) do usuário anterior, já que o HTML
// ficava congelado do jeito que estava antes do logout/login.
window.addEventListener("pageshow", (e) => {
  if (e.persisted) window.location.reload();
});

// Dispositivo e Configurações são compartilhadas pelos dois tipos de conta,
// com o menu fixo no HTML. Sem ajustar em tempo de execução, um item
// aparecia numa tela e sumia na seguinte para o mesmo usuário.
//
// Clínica: sem Agenda (é por profissional), com Consultas.
// Profissional: com Agenda, sem Consultas (que é a visão da instituição).
(function ajustarNavPorTipoDeConta() {
  const sessao = getSessao();
  if (!sessao) return;

  const nav = document.querySelector(".sidebar .nav");
  if (!nav) return;

  const ehClinica = sessao.tipo === "CLINICA";
  const seletorAgenda = 'a[href$="agenda.html"], a[href$="agenda-escuro.html"]';
  const seletorConsultas = 'a[href$="consultas.html"], a[href$="consultas-escuro.html"]';

  document.querySelectorAll(ehClinica ? seletorAgenda : seletorConsultas).forEach((el) => el.remove());

  if (!ehClinica || nav.querySelector(seletorConsultas)) return;

  // Insere Consultas antes de Perfil, mantendo a ordem das demais telas.
  const link = document.createElement("a");
  link.href = paginaTema("consultas");
  link.innerHTML =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
    '<rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/>' +
    '<line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg> Consultas';

  const perfil = Array.from(nav.querySelectorAll("a")).find((a) => /perfil/i.test(a.textContent));
  nav.insertBefore(link, perfil || null);
})();

// Mostra a foto de perfil do usuário logado onde o avatar aparece
// (cabeçalho da instituição/profissional, topo mobile, tela de perfil).
(function aplicarAvatarSessao() {
  const sessao = getSessao();
  if (!sessao || !sessao.foto) return;
  const url = urlFoto(sessao.foto);
  document.querySelectorAll(".inst-avatar, .mobile-avatar, .avatar").forEach((el) => {
    el.style.backgroundImage = `url("${url}")`;
    el.style.backgroundSize = "cover";
    el.style.backgroundPosition = "center";
  });
})();

// Cadastrar Profissional — escolha da foto de perfil (mostra prévia
// e guarda o arquivo para enviar junto com o cadastro).
let arquivoFotoProfissional = null;
(function configurarSeletorFoto() {
  const pickers = document.querySelectorAll("[data-avatar-picker]");
  const inputFoto = document.getElementById("pfoto");
  if (!pickers.length || !inputFoto) return;

  pickers.forEach((btn) => {
    btn.addEventListener("click", () => inputFoto.click());
  });

  // Todo picker abre o seletor de arquivo, mas só os que são o próprio
  // avatar recebem a prévia. Em editar-paciente o botão de texto "Alterar
  // foto" também é um picker — pintar a foto nele deixava a imagem atrás
  // do texto do botão.
  const alvosPreview = Array.from(pickers).filter(
    (el) => el.classList.contains("form-avatar") || el.classList.contains("avatar-lg")
  );

  inputFoto.addEventListener("change", () => {
    const arquivo = inputFoto.files && inputFoto.files[0];
    if (!arquivo) return;
    arquivoFotoProfissional = arquivo;
    const url = URL.createObjectURL(arquivo);
    alvosPreview.forEach((el) => {
      el.style.backgroundImage = `url("${url}")`;
      el.style.backgroundSize = "cover";
      el.style.backgroundPosition = "center";
    });
  });
})();

// Small interactive stubs — mirrors what buttons would do in the app
document.addEventListener("click", (e) => {
  const target = e.target.closest("[data-action]");
  if (!target) return;
  const action = target.getAttribute("data-action");
  switch (action) {
    case "edit-profile": {
      const sessaoAtual = getSessao();
      window.location.href =
        sessaoAtual && sessaoAtual.tipo === "CLINICA"
          ? paginaTema("editar-perfil-instituicao")
          : paginaTema("editar-perfil-profissional");
      break;
    }
    case "go-list": {
      const sessaoAtual = getSessao();
      window.location.href =
        sessaoAtual && sessaoAtual.tipo === "CLINICA" ? paginaTema("instituicao") : paginaTema("profissional");
      break;
    }
    case "go-profile": {
      const sessaoAtual = getSessao();
      window.location.href =
        sessaoAtual && sessaoAtual.tipo === "CLINICA" ? paginaTema("perfil-instituicao") : paginaTema("perfil-profissional");
      break;
    }
    case "back":
      history.length > 1 ? history.back() : (window.location.href = "./");
      break;
    case "add-fisio":
      window.location.href = paginaTema("cadastrar-profissional");
      break;
    case "logout":
      e.preventDefault();
      localStorage.removeItem("rehabit_usuario");
      window.location.href = paginaLogin();
      break;
    case "add-patient":
      window.location.href = paginaTema("cadastrar-paciente");
      break;
    case "add-session":
      window.location.href = paginaTema("cadastrar-sessao");
      break;
    default:
      break;
  }
});

// Cadastro de profissional — envia os dados para a API vinculando
// o profissional à instituição atualmente logada.
const cadastrarProfissionalForm = document.getElementById("cadastrarProfissionalForm");
if (cadastrarProfissionalForm) {
  cadastrarProfissionalForm.addEventListener("submit", async (e) => {
    e.preventDefault();

    const sessao = getSessao();
    if (!sessao || sessao.tipo !== "CLINICA") {
      RehabitToast.erro("Apenas uma instituição logada pode cadastrar profissionais.");
      return;
    }

    const coffito = document.getElementById("pid").value.trim();
    const nome = document.getElementById("pnome").value.trim();
    const telefone = document.getElementById("ptel").value.trim();
    const email = document.getElementById("pmail").value.trim();
    const senha = document.getElementById("psenha").value;
    const especialidade = document.getElementById("pesp").value.trim();
    const localidade = document.getElementById("ploc").value.trim();
    const descricao = document.getElementById("pdesc").value.trim();

    if (!coffito || !nome || !email || !senha) {
      RehabitToast.erro("Preencha COFFITO, nome, e-mail e senha.");
      return;
    }
    if (senha.length < 6) {
      RehabitToast.erro("A senha deve ter ao menos 6 caracteres.");
      return;
    }

    const submitBtn = cadastrarProfissionalForm.querySelector(".btn-primary");
    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = "Cadastrando...";
    RehabitLoader.show("Cadastrando");

    try {
      let foto = null;
      if (arquivoFotoProfissional) {
        const formData = new FormData();
        formData.append("arquivo", arquivoFotoProfissional);
        const uploadResponse = await buscar(`${API_BASE_URL}/uploads`, {
          method: "POST",
          body: formData,
        });
        const uploadDados = await uploadResponse.json().catch(() => ({}));
        if (!uploadResponse.ok) {
          RehabitToast.erro(uploadDados.mensagem || "Não foi possível enviar a foto de perfil.");
          return;
        }
        foto = uploadDados.url;
      }

      const response = await fetch(`${API_BASE_URL}/fisioterapeutas`, {
        method: "POST",
        headers: cabecalhosAutenticados({ "Content-Type": "application/json" }),
        body: JSON.stringify({
          idClinica: sessao.id,
          nome,
          coffito,
          email,
          senha,
          telefone: telefone || null,
          especialidade: especialidade || null,
          localidade: localidade || null,
          descricao: descricao || null,
          foto,
        }),
      });

      const dados = await response.json().catch(() => ({}));

      if (!response.ok) {
        RehabitToast.erro(dados.mensagem || "Não foi possível cadastrar o profissional.");
        return;
      }

      RehabitToast.sucesso("Profissional cadastrado com sucesso.");
      setTimeout(() => {
        window.location.href = paginaTema("instituicao");
      }, 1200);
    } catch (err) {
      RehabitToast.erro("Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.");
    } finally {
      RehabitLoader.hide();
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
}

// Configurações — o checkbox "Tema Escuro" navega para a variante
// clara/escura da própria tela de configurações (o projeto usa páginas
// HTML estáticas separadas por tema, sem alternância dinâmica).
(function configurarToggleTema() {
  const darkThemeToggle = document.getElementById("darkTheme");
  if (!darkThemeToggle) return;

  darkThemeToggle.addEventListener("change", () => {
    const atual = window.location.pathname;
    const destino = darkThemeToggle.checked
      ? atual.replace(/\.html$/, "-escuro.html")
      : atual.replace(/-escuro\.html$/, ".html");
    if (destino !== atual) window.location.href = destino;
  });
})();

// Configurações — expande/recolhe as seções (ex.: "Preferências").
(function configurarAccordions() {
  document.querySelectorAll(".settings-toggle").forEach((toggle) => {
    toggle.addEventListener("click", () => {
      const expandido = toggle.getAttribute("aria-expanded") === "true";
      toggle.setAttribute("aria-expanded", String(!expandido));
    });
  });
})();
