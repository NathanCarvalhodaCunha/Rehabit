// Rehabit Auth — vanilla JS mirroring the React behavior

// Account type selector (register page)

/*
document.querySelectorAll('.account-type button').forEach((btn) => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.account-type button').forEach((b) => {
      b.classList.remove('active');
      b.setAttribute('aria-selected', 'false');
    });
    btn.classList.add('active');
    btn.setAttribute('aria-selected', 'true');
  });
});

// Login form — matches React: preventDefault only
const loginForm = document.getElementById('loginForm');
if (loginForm) {
  loginForm.addEventListener('submit', (e) => {
    e.preventDefault();
  });
}

// Register form — matches React: preventDefault only
const registerForm = document.getElementById('registerForm');
if (registerForm) {
  registerForm.addEventListener('submit', (e) => {
    e.preventDefault();
  });
}

*/



// Rehabit Auth — vanilla JS mirroring the React behavior

const API_BASE_URL =
  location.protocol === "file:" || location.hostname === "localhost"
    ? "http://localhost:8080/api"
    : "https://rehabit-api-4tex.onrender.com/api";
// Tornar acessível globalmente para outros scripts
window.API_BASE_URL = API_BASE_URL;

// Login form — envia os dados para a API e redireciona conforme o tipo de conta
const loginForm = document.getElementById('loginForm');
if (loginForm) {
  loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const submitBtn = loginForm.querySelector('.btn-primary');
    const email = document.getElementById('email').value.trim();
    const senha = document.getElementById('password').value;

    if (!email || !senha) return;

    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = 'Entrando...';
    RehabitLoader.show('Entrando');

    try {
      const response = await fetch(`${API_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, senha }),
      });

      const dados = await response.json().catch(() => ({}));

      if (!response.ok) {
        RehabitToast.erro(dados.mensagem || 'E-mail ou senha inválidos.');
        return;
      }

      localStorage.setItem('rehabit_usuario', JSON.stringify(dados));

      const sufixo = document.body.classList.contains('dark') ? '-escuro' : '';
      const destino =
        dados.tipo === 'CLINICA' ? `../Software/instituicao${sufixo}.html` : `../Software/profissional${sufixo}.html`;
      window.location.href = destino;
    } catch (err) {
      RehabitToast.erro('Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.');
    } finally {
      RehabitLoader.hide();
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
}

// ---------------------------------------------------------------------------
// Recuperação de senha por e-mail
// ---------------------------------------------------------------------------

// Sufixo do tema, para os redirecionamentos entre as telas claras e escuras.
function rehabitSufixoTema() {
  return document.body.classList.contains('dark') ? '-escuro' : '';
}

// Passo 1 — "Esqueci minha senha": pede o e-mail e dispara o envio do código.
const esqueciSenhaForm = document.getElementById('esqueciSenhaForm');
if (esqueciSenhaForm) {
  const painelEnviado = document.getElementById('esqueciSenhaEnviado');
  const mensagemEnviado = document.getElementById('esqueciSenhaMensagem');
  const linkRedefinir = document.getElementById('irRedefinir');
  const linkReenviar = document.getElementById('reenviarCodigo');
  let ultimoEmail = '';

  async function pedirCodigoRecuperacao(email, botao) {
    const textoOriginal = botao ? botao.textContent : '';
    if (botao) {
      botao.disabled = true;
      botao.textContent = 'Enviando...';
    }
    RehabitLoader.show('Enviando e-mail');

    try {
      const response = await fetch(`${API_BASE_URL}/auth/esqueci-senha`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });
      const dados = await response.json().catch(() => ({}));

      if (!response.ok) {
        RehabitToast.erro(dados.mensagem || 'Não foi possível enviar o e-mail de recuperação.');
        return false;
      }

      ultimoEmail = email;
      // O e-mail vai junto na URL só para poupar a pessoa de digitar de novo;
      // sem o código que chegou na caixa de entrada ele não serve para nada.
      const destino = `redefinir-senha${rehabitSufixoTema()}.html?email=${encodeURIComponent(email)}`;
      if (linkRedefinir) linkRedefinir.setAttribute('href', destino);
      if (mensagemEnviado && dados.mensagem) mensagemEnviado.textContent = dados.mensagem;
      if (painelEnviado) {
        esqueciSenhaForm.hidden = true;
        painelEnviado.hidden = false;
      }
      RehabitToast.sucesso('E-mail de recuperação enviado.');
      return true;
    } catch (err) {
      RehabitToast.erro('Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.');
      return false;
    } finally {
      RehabitLoader.hide();
      if (botao) {
        botao.disabled = false;
        botao.textContent = textoOriginal;
      }
    }
  }

  esqueciSenhaForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('email').value.trim();
    if (!email) {
      RehabitToast.erro('Informe o e-mail da sua conta.');
      return;
    }
    await pedirCodigoRecuperacao(email, esqueciSenhaForm.querySelector('.btn-primary'));
  });

  if (linkReenviar) {
    linkReenviar.addEventListener('click', async (e) => {
      e.preventDefault();
      if (ultimoEmail) await pedirCodigoRecuperacao(ultimoEmail, null);
    });
  }
}

// Passo 2 — "Nova senha": aceita tanto o token do link do e-mail quanto o
// código de 6 dígitos digitado na tela (o link não existe quando o site é
// aberto direto do arquivo, por file://).
const redefinirSenhaForm = document.getElementById('redefinirSenhaForm');
if (redefinirSenhaForm) {
  const parametros = new URLSearchParams(location.search);
  const token = (parametros.get('token') || '').trim();
  const emailInput = document.getElementById('email');
  const codigoInput = document.getElementById('codigo');
  const subtitulo = document.getElementById('redefinirSubtitulo');
  const camposCodigo = redefinirSenhaForm.querySelectorAll('[data-campo-codigo]');

  if (emailInput && parametros.get('email')) {
    emailInput.value = parametros.get('email');
  }

  // Só aceita dígitos no campo do código.
  if (codigoInput) {
    codigoInput.addEventListener('input', () => {
      codigoInput.value = codigoInput.value.replace(/\D/g, '').slice(0, 6);
    });
  }

  // Fica falso se o token da URL não valer mais: aí o envio passa a usar o
  // e-mail + código digitados.
  let usarToken = Boolean(token);

  // Veio pelo link: confere o token antes de mostrar o formulário, para a
  // pessoa não digitar a senha nova à toa se o link já venceu.
  if (token) {
    camposCodigo.forEach((campo) => { campo.hidden = true; });

    (async () => {
      RehabitLoader.show('Verificando link');
      try {
        const response = await fetch(
          `${API_BASE_URL}/auth/recuperar-senha/validar?token=${encodeURIComponent(token)}`
        );
        const dados = await response.json().catch(() => ({}));

        if (!response.ok || !dados.valido) {
          // Link vencido ou já usado: reabre os campos para a pessoa tentar
          // pelo código, em vez de deixar a tela num beco sem saída.
          RehabitToast.erro(dados.mensagem || 'Este link não é mais válido. Peça um novo.');
          camposCodigo.forEach((campo) => { campo.hidden = false; });
          if (subtitulo) subtitulo.textContent = 'Use o código que enviamos para o seu e-mail';
          usarToken = false;
          return;
        }
        if (subtitulo) subtitulo.textContent = `Criando uma nova senha para ${dados.email}`;
      } catch (err) {
        RehabitToast.erro('Não foi possível conectar ao servidor. Verifique sua conexão.');
        camposCodigo.forEach((campo) => { campo.hidden = false; });
        usarToken = false;
      } finally {
        RehabitLoader.hide();
      }
    })();
  }


  redefinirSenhaForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const submitBtn = redefinirSenhaForm.querySelector('.btn-primary');
    const novaSenha = document.getElementById('novaSenha').value;
    const confirmarSenha = document.getElementById('confirmarSenha').value;
    const email = emailInput ? emailInput.value.trim() : '';
    const codigo = codigoInput ? codigoInput.value.trim() : '';

    if (!usarToken) {
      if (!email) {
        RehabitToast.erro('Informe o e-mail da sua conta.');
        return;
      }
      if (codigo.length !== 6) {
        RehabitToast.erro('Informe o código de 6 dígitos que enviamos por e-mail.');
        return;
      }
    }
    if (novaSenha.length < 6) {
      RehabitToast.erro('A nova senha deve ter ao menos 6 caracteres.');
      return;
    }
    if (novaSenha !== confirmarSenha) {
      RehabitToast.erro('As senhas não conferem.');
      return;
    }

    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = 'Salvando...';
    RehabitLoader.show('Redefinindo senha');

    try {
      const corpo = usarToken ? { token, novaSenha } : { email, codigo, novaSenha };
      const response = await fetch(`${API_BASE_URL}/auth/recuperar-senha`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(corpo),
      });
      const dados = await response.json().catch(() => ({}));

      if (!response.ok) {
        RehabitToast.erro(dados.mensagem || 'Não foi possível redefinir sua senha.');
        return;
      }

      RehabitToast.sucesso('Senha redefinida com sucesso. Faça login com a nova senha.');
      setTimeout(() => {
        window.location.href = `login${rehabitSufixoTema()}.html`;
      }, 1200);
    } catch (err) {
      RehabitToast.erro('Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.');
    } finally {
      RehabitLoader.hide();
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
}

// Register form — matches React: preventDefault only
// (o envio para a API acontece após o preenchimento do pop-up de
// informações opcionais, em modal.js)
const registerForm = document.getElementById('registerForm');
if (registerForm) {
  registerForm.addEventListener('submit', (e) => {
    e.preventDefault();
  });
}
