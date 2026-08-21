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

  function render(tipo) {
    var campos = CAMPOS[tipo] || CAMPOS.profissional;
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
    avatarBtn.innerHTML = '<img alt="Foto de perfil" src="' + URL.createObjectURL(f) + '" />';
  });

  overlay.querySelector('.rh-skip').addEventListener('click', close);
  overlay.querySelector('.rh-submit').addEventListener('click', close);
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
      var ativo = document.querySelector('.account-type button.active');
      open(ativo ? ativo.getAttribute('data-type') : 'profissional');
    });
  }
})();
