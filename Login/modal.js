/* Rehabit — Pop-up "Informações opcionais"
 * Uso:
 *   RehabitModal.open('profissional')  // ou 'instituicao'
 *   RehabitModal.close()
 * O pop-up é injetado automaticamente no <body>.
 */
(function () {
  var PENCIL =
    '<svg viewBox="0 0 24 24" fill="none" stroke="#6B7280" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>';

  var CAMPOS = {
    profissional: [
      { id: 'coffito', label: 'COFFITO', type: 'text' },
      { id: 'telefone', label: 'Telefone', type: 'tel' },
      { id: 'especialidade', label: 'Especialidade', type: 'text' },
      { id: 'localidade', label: 'Localidade', type: 'text' },
      { id: 'descricao', label: 'Descrição', type: 'textarea', placeholder: 'Escreva sobre você' },
    ],
    instituicao: [
      { id: 'cnpj', label: 'CNPJ', type: 'text' },
      { id: 'telefone', label: 'Telefone', type: 'tel' },
      { id: 'endereco', label: 'Endereço', type: 'text' },
      { id: 'subtitulo', label: 'Subtítulo', type: 'text' },
      { id: 'descricao', label: 'Descrição', type: 'textarea', placeholder: 'Escreva sobre a instituição' },
    ],
  };

  var overlay = document.createElement('div');
  overlay.className = 'rh-overlay';
  overlay.setAttribute('role', 'dialog');
  overlay.setAttribute('aria-modal', 'true');
  overlay.setAttribute('aria-labelledby', 'rhModalTitle');
  overlay.innerHTML =
    '<div class="rh-modal">' +
      '<h2 id="rhModalTitle">Informações opcionais</h2>' +
      '<p class="rh-sub">As informações podem ser adicionadas após o cadastro</p>' +
      '<div class="rh-avatar-wrap">' +
        '<button type="button" class="rh-avatar" aria-label="Escolher foto de perfil"></button>' +
        '<button type="button" class="rh-avatar-label">Foto de Perfil' + PENCIL + '</button>' +
        '<input type="file" accept="image/*" hidden />' +
      '</div>' +
      '<form class="rh-fields" novalidate></form>' +
      '<div class="rh-actions">' +
        '<button type="button" class="rh-skip">Pular</button>' +
        '<button type="button" class="rh-submit">Cadastrar</button>' +
      '</div>' +
    '</div>';
  document.body.appendChild(overlay);

  var fieldsEl = overlay.querySelector('.rh-fields');
  var avatarBtn = overlay.querySelector('.rh-avatar');
  var avatarLabel = overlay.querySelector('.rh-avatar-label');
  var fileInput = overlay.querySelector('input[type=file]');
  var arquivoSelecionado = null;

  function render(tipo) {
    var campos = CAMPOS[tipo] || CAMPOS.instituicao;
    fieldsEl.innerHTML = campos
      .map(function (c) {
        var input =
          c.type === 'textarea'
            ? '<textarea id="rh-' + c.id + '" placeholder="' + (c.placeholder || '') + '"></textarea>'
            : '<input id="rh-' + c.id + '" type="' + c.type + '" placeholder="' + (c.placeholder || '') + '" />';
        return '<div class="rh-field"><label for="rh-' + c.id + '">' + c.label + '</label>' + input + '</div>';
      })
      .join('');
  }

  function open(tipo) {
    render(tipo);
    overlay.classList.add('open');
    document.body.style.overflow = 'hidden';
  }
  function close() {
    overlay.classList.remove('open');
    document.body.style.overflow = '';
  }

  avatarBtn.addEventListener('click', function () { fileInput.click(); });
  avatarLabel.addEventListener('click', function () { fileInput.click(); });
  fileInput.addEventListener('change', function () {
    var f = fileInput.files && fileInput.files[0];
    if (!f) return;
    arquivoSelecionado = f;
    avatarBtn.innerHTML = '<img alt="Foto de perfil" src="' + URL.createObjectURL(f) + '" />';
  });

  // Tanto "Cadastrar" quanto "Pular" concluem o cadastro: os campos deste
  // pop-up são todos opcionais (exceto CNPJ, exigido pela própria API), então
  // "Pular" significa "cadastrar sem preencher esses extras", não "cancelar".
  async function submeter() {
    var submitBtn = overlay.querySelector('.rh-submit');
    var skipBtn = overlay.querySelector('.rh-skip');
    submitBtn.disabled = true;
    skipBtn.disabled = true;
    var originalText = submitBtn.textContent;
    submitBtn.textContent = 'Cadastrando...';

    function liberar() {
      submitBtn.disabled = false;
      skipBtn.disabled = false;
      submitBtn.textContent = originalText;
    }

    var mainForm = document.getElementById('registerForm');
    if (!mainForm) {
      RehabitToast.erro('Formulário não encontrado.');
      liberar();
      return;
    }

    var name = (mainForm.querySelector('#name') && mainForm.querySelector('#name').value.trim()) || '';
    var email = (mainForm.querySelector('#email') && mainForm.querySelector('#email').value.trim()) || '';
    var senha = (mainForm.querySelector('#password') && mainForm.querySelector('#password').value) || '';
    var confirm = (mainForm.querySelector('#confirm') && mainForm.querySelector('#confirm').value) || '';

    if (!name || !email || !senha) {
      RehabitToast.erro('Preencha nome, e-mail e senha.');
      liberar();
      return;
    }
    if (senha !== confirm) {
      RehabitToast.erro('Senhas não conferem.');
      liberar();
      return;
    }

    var extras = {};
    overlay.querySelectorAll('.rh-fields [id^="rh-"]').forEach(function (el) {
      var id = el.id.replace(/^rh-/, '');
      extras[id] = (el.value || '').trim();
    });

    var tipo = 'instituicao';

    if (!extras.cnpj) {
      RehabitToast.erro('Informe o CNPJ da instituição.');
      liberar();
      return;
    }

    var base = window.API_BASE_URL || '';
    var foto = null;
    RehabitLoader.show('Cadastrando');
    if (arquivoSelecionado) {
      try {
        var formData = new FormData();
        formData.append('arquivo', arquivoSelecionado);
        var uploadRes = await fetch(base + '/uploads', { method: 'POST', body: formData });
        var uploadData = await uploadRes.json().catch(function () { return {}; });
        if (!uploadRes.ok) {
          RehabitToast.erro(uploadData.mensagem || 'Não foi possível enviar a foto de perfil.');
          RehabitLoader.hide();
          liberar();
          return;
        }
        foto = uploadData.url;
      } catch (err) {
        RehabitToast.erro('Não foi possível enviar a foto de perfil. Verifique sua conexão.');
        RehabitLoader.hide();
        liberar();
        return;
      }
    }

    var payload = Object.assign(
      { nome: name, email: email, senha: senha, tipo: tipo, foto: foto },
      extras
    );

    try {
      var res = await fetch(base + '/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      var data = await res.json().catch(function () { return {}; });
      if (!res.ok) {
        RehabitToast.erro(data.mensagem || 'Erro ao cadastrar (status ' + res.status + ').');
        return;
      }
      RehabitToast.sucesso('Cadastro realizado com sucesso.');
      close();
      setTimeout(function () {
        window.location.href = document.body.classList.contains('dark') ? 'login-escuro.html' : 'login.html';
      }, 1200);
    } catch (err) {
      RehabitToast.erro('Não foi possível conectar ao servidor. Verifique sua conexão.');
    } finally {
      RehabitLoader.hide();
      liberar();
    }
  }

  overlay.querySelector('.rh-skip').addEventListener('click', submeter);
  overlay.querySelector('.rh-submit').addEventListener('click', submeter);
  overlay.addEventListener('mousedown', function (e) {
    if (e.target === overlay) close();
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && overlay.classList.contains('open')) close();
  });

  window.RehabitModal = { open: open, close: close };

  // Integração com a tela de cadastro: abre o pop-up ao enviar o formulário
  var form = document.getElementById('registerForm');
  if (form) {
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      open('instituicao');
    });
  }
})();
