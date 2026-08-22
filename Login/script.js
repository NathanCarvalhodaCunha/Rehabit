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

const API_BASE_URL = "http://localhost:8080/api";
// Tornar acessível globalmente para outros scripts
window.API_BASE_URL = API_BASE_URL;

// Account type selector (register page)
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
        alert(dados.mensagem || 'E-mail ou senha inválidos.');
        return;
      }

      localStorage.setItem('rehabit_usuario', JSON.stringify(dados));

      const sufixo = document.body.classList.contains('dark') ? '-escuro' : '';
      const destino =
        dados.tipo === 'CLINICA' ? `../Software/instituicao${sufixo}.html` : `../Software/profissional${sufixo}.html`;
      window.location.href = destino;
    } catch (err) {
      alert('Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.');
    } finally {
      RehabitLoader.hide();
      submitBtn.disabled = false;
      submitBtn.textContent = textoOriginal;
    }
  });
}

// Esqueci minha senha — redefinição direta (sem e-mail): confirma a posse
// da conta pelo CNPJ/COFFITO e já salva a nova senha.
const esqueciSenhaForm = document.getElementById('esqueciSenhaForm');
if (esqueciSenhaForm) {
  esqueciSenhaForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const submitBtn = esqueciSenhaForm.querySelector('.btn-primary');
    const email = document.getElementById('email').value.trim();
    const documento = document.getElementById('documento').value.trim();
    const novaSenha = document.getElementById('novaSenha').value;
    const confirmarSenha = document.getElementById('confirmarSenha').value;

    if (!email || !documento || !novaSenha) return;
    if (novaSenha.length < 6) {
      alert('A nova senha deve ter ao menos 6 caracteres.');
      return;
    }
    if (novaSenha !== confirmarSenha) {
      alert('As senhas não conferem.');
      return;
    }

    submitBtn.disabled = true;
    const textoOriginal = submitBtn.textContent;
    submitBtn.textContent = 'Redefinindo...';
    RehabitLoader.show('Redefinindo senha');

    try {
      const response = await fetch(`${API_BASE_URL}/auth/redefinir-senha`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, documento, novaSenha }),
      });

      if (!response.ok) {
        const dados = await response.json().catch(() => ({}));
        alert(dados.mensagem || 'Não foi possível redefinir sua senha.');
        return;
      }

      alert('Senha redefinida com sucesso. Faça login com a nova senha.');
      window.location.href = document.body.classList.contains('dark') ? 'login-escuro.html' : 'login.html';
    } catch (err) {
      alert('Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.');
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
