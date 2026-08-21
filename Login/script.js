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

      const destino = dados.tipo === 'CLINICA' ? '../Software/instituicao.html' : '../Software/profissional.html';
      window.location.href = destino;
    } catch (err) {
      alert('Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.');
    } finally {
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
