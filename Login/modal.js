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

  // Segundo pop-up: confirmação do e-mail. Fica separado do primeiro para
  // não misturar dois passos na mesma caixa — a pessoa volta para os
  // "extras" se quiser corrigir alguma coisa.
  var overlayCodigo = document.createElement('div');
  overlayCodigo.className = 'rh-overlay';
  overlayCodigo.setAttribute('role', 'dialog');
  overlayCodigo.setAttribute('aria-modal', 'true');
  overlayCodigo.setAttribute('aria-labelledby', 'rhCodigoTitulo');
  overlayCodigo.innerHTML =
    '<div class="rh-modal rh-modal--codigo">' +
      '<h2 id="rhCodigoTitulo">Confirme seu e-mail</h2>' +
      '<p class="rh-sub rh-codigo-destino">Enviamos um código de 6 dígitos para o seu e-mail</p>' +
      '<div class="rh-field rh-codigo-campo">' +
        '<label for="rh-codigo">Código de 6 dígitos</label>' +
        '<input id="rh-codigo" class="rh-codigo-input" type="text" inputmode="numeric" ' +
               'autocomplete="one-time-code" maxlength="6" placeholder="000000" />' +
      '</div>' +
      '<p class="rh-codigo-ajuda">Não chegou? Olhe também na caixa de spam. ' +
        '<button type="button" class="rh-reenviar">Enviar de novo</button></p>' +
      '<div class="rh-actions">' +
        '<button type="button" class="rh-skip rh-voltar">Voltar</button>' +
        '<button type="button" class="rh-submit rh-confirmar">Confirmar e cadastrar</button>' +
      '</div>' +
    '</div>';
  document.body.appendChild(overlayCodigo);

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
  //
  // Antes de criar a conta, o e-mail passa por uma confirmação: a API manda um
  // código de 6 dígitos para o endereço informado e só cadastra depois que ele
  // é digitado aqui. É o que garante que o e-mail existe de verdade — sem
  // isso, "meuemail@qualquercoisa.com" viraria uma conta sem dono e sem como
  // recuperar a senha.
  var dadosPendentes = null;     // o que foi preenchido, à espera da confirmação
  var codigoEnviadoPara = null;  // e-mail que já recebeu um código nesta visita
  var emailConfirmado = null;    // e-mail que já passou pela confirmação

  function base() {
    return window.API_BASE_URL || '';
  }

  function lerFormulario() {
    var mainForm = document.getElementById('registerForm');
    if (!mainForm) {
      RehabitToast.erro('Formulário não encontrado.');
      return null;
    }

    var name = (mainForm.querySelector('#name') && mainForm.querySelector('#name').value.trim()) || '';
    var email = (mainForm.querySelector('#email') && mainForm.querySelector('#email').value.trim()) || '';
    var senha = (mainForm.querySelector('#password') && mainForm.querySelector('#password').value) || '';
    var confirm = (mainForm.querySelector('#confirm') && mainForm.querySelector('#confirm').value) || '';

    if (!name || !email || !senha) {
      RehabitToast.erro('Preencha nome, e-mail e senha.');
      return null;
    }
    if (senha.length < 6) {
      RehabitToast.erro('A senha deve ter ao menos 6 caracteres.');
      return null;
    }
    if (senha !== confirm) {
      RehabitToast.erro('Senhas não conferem.');
      return null;
    }

    var extras = {};
    overlay.querySelectorAll('.rh-fields [id^="rh-"]').forEach(function (el) {
      var id = el.id.replace(/^rh-/, '');
      extras[id] = (el.value || '').trim();
    });

    if (!extras.cnpj) {
      RehabitToast.erro('Informe o CNPJ da instituição.');
      return null;
    }

    return { nome: name, email: email, senha: senha, tipo: 'instituicao', extras: extras };
  }

  /** A API diz se este ambiente exige o código (depende de ter SMTP configurado). */
  async function confirmacaoObrigatoria() {
    try {
      var res = await fetch(base() + '/auth/verificar-email/obrigatorio');
      if (!res.ok) return false;
      return (await res.json()) === true;
    } catch (err) {
      // Sem resposta, segue direto para o cadastro: quem barra de verdade é a
      // API, que recusa o /register sem e-mail confirmado.
      return false;
    }
  }

  /** Pede o código para o e-mail informado. */
  async function enviarCodigo(email, silencioso) {
    RehabitLoader.show('Enviando código');
    try {
      var res = await fetch(base() + '/auth/verificar-email/enviar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email }),
      });
      var data = await res.json().catch(function () { return {}; });
      if (!res.ok) {
        RehabitToast.erro(data.mensagem || 'Não foi possível enviar o código de confirmação.');
        return null;
      }
      if (!silencioso) RehabitToast.info(data.mensagem || 'Código enviado.');
      return data;
    } catch (err) {
      RehabitToast.erro('Não foi possível conectar ao servidor. Verifique sua conexão.');
      return null;
    } finally {
      RehabitLoader.hide();
    }
  }

  function abrirCodigo(destino) {
    var texto = overlayCodigo.querySelector('.rh-codigo-destino');
    if (texto) {
      texto.textContent = destino
        ? 'Enviamos um código de 6 dígitos para ' + destino
        : 'Enviamos um código de 6 dígitos para o seu e-mail';
    }
    overlay.classList.remove('open');
    overlayCodigo.classList.add('open');
    document.body.style.overflow = 'hidden';
    var campo = overlayCodigo.querySelector('.rh-codigo-input');
    if (campo) {
      campo.value = '';
      campo.focus();
    }
  }

  function fecharCodigo(voltarParaExtras) {
    overlayCodigo.classList.remove('open');
    if (voltarParaExtras) {
      overlay.classList.add('open');
    } else {
      document.body.style.overflow = '';
    }
  }

  function travarBotoes(travado, texto) {
    [overlay, overlayCodigo].forEach(function (el) {
      var submit = el.querySelector('.rh-submit');
      var skip = el.querySelector('.rh-skip');
      if (submit) {
        submit.disabled = travado;
        if (texto && travado) {
          if (!submit.dataset.textoOriginal) submit.dataset.textoOriginal = submit.textContent;
          submit.textContent = texto;
        } else if (!travado && submit.dataset.textoOriginal) {
          submit.textContent = submit.dataset.textoOriginal;
          delete submit.dataset.textoOriginal;
        }
      }
      if (skip) skip.disabled = travado;
    });
  }

  /** Passo 1: valida o formulário e ou pede o código, ou cadastra direto. */
  async function submeter() {
    travarBotoes(true, 'Aguarde...');
    try {
      var dados = lerFormulario();
      if (!dados) return;

      dadosPendentes = dados;

      if (!(await confirmacaoObrigatoria())) {
        await finalizarCadastro();
        return;
      }

      // Este e-mail já foi confirmado nesta visita (a pessoa voltou para
      // corrigir o CNPJ, por exemplo): não precisa de código de novo.
      if (emailConfirmado === dados.email) {
        await finalizarCadastro();
        return;
      }

      // Já mandamos um código para este endereço: reabre a caixa em vez de
      // pedir outro — a API só deixa reenviar depois de um minuto.
      if (codigoEnviadoPara === dados.email) {
        abrirCodigo(null);
        return;
      }

      var envio = await enviarCodigo(dados.email, true);
      if (!envio) return;
      codigoEnviadoPara = dados.email;
      abrirCodigo(envio.email);
    } finally {
      travarBotoes(false);
    }
  }

  /** Passo 2: confere o código digitado e, dando certo, cria a conta. */
  async function confirmarCodigo() {
    var campo = overlayCodigo.querySelector('.rh-codigo-input');
    var codigo = campo ? campo.value.trim() : '';
    if (codigo.length !== 6) {
      RehabitToast.erro('Digite os 6 dígitos do código que enviamos.');
      return;
    }
    if (!dadosPendentes) {
      RehabitToast.erro('Preencha o cadastro novamente.');
      fecharCodigo(false);
      return;
    }

    travarBotoes(true, 'Confirmando...');
    RehabitLoader.show('Confirmando e-mail');
    try {
      var res = await fetch(base() + '/auth/verificar-email/confirmar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: dadosPendentes.email, codigo: codigo }),
      });
      var data = await res.json().catch(function () { return {}; });
      if (!res.ok) {
        RehabitToast.erro(data.mensagem || 'Código incorreto.');
        if (campo) campo.select();
        return;
      }
      emailConfirmado = dadosPendentes.email;
    } catch (err) {
      RehabitToast.erro('Não foi possível conectar ao servidor. Verifique sua conexão.');
      return;
    } finally {
      RehabitLoader.hide();
      travarBotoes(false);
    }

    await finalizarCadastro();
  }

  /** Passo 3: sobe a foto (se houver) e cria a conta. */
  async function finalizarCadastro() {
    var dados = dadosPendentes;
    if (!dados) return;

    travarBotoes(true, 'Cadastrando...');
    RehabitLoader.show('Cadastrando');

    var foto = null;
    if (arquivoSelecionado) {
      try {
        var formData = new FormData();
        formData.append('arquivo', arquivoSelecionado);
        var uploadRes = await fetch(base() + '/uploads', { method: 'POST', body: formData });
        var uploadData = await uploadRes.json().catch(function () { return {}; });
        if (!uploadRes.ok) {
          RehabitToast.erro(uploadData.mensagem || 'Não foi possível enviar a foto de perfil.');
          RehabitLoader.hide();
          travarBotoes(false);
          return;
        }
        foto = uploadData.url;
      } catch (err) {
        RehabitToast.erro('Não foi possível enviar a foto de perfil. Verifique sua conexão.');
        RehabitLoader.hide();
        travarBotoes(false);
        return;
      }
    }

    var payload = Object.assign(
      { nome: dados.nome, email: dados.email, senha: dados.senha, tipo: dados.tipo, foto: foto },
      dados.extras
    );

    try {
      var res = await fetch(base() + '/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      var data = await res.json().catch(function () { return {}; });
      if (!res.ok) {
        RehabitToast.erro(data.mensagem || 'Erro ao cadastrar (status ' + res.status + ').');
        // Volta para os extras: o que precisa de conserto (CNPJ repetido, por
        // exemplo) está lá, não na caixa do código.
        if (overlayCodigo.classList.contains('open')) fecharCodigo(true);
        return;
      }
      RehabitToast.sucesso('Cadastro realizado com sucesso.');
      dadosPendentes = null;
      fecharCodigo(false);
      close();
      setTimeout(function () {
        window.location.href = document.body.classList.contains('dark') ? 'login-escuro.html' : 'login.html';
      }, 1200);
    } catch (err) {
      RehabitToast.erro('Não foi possível conectar ao servidor. Verifique sua conexão.');
    } finally {
      RehabitLoader.hide();
      travarBotoes(false);
    }
  }

  // Só dígitos no campo do código.
  var campoCodigo = overlayCodigo.querySelector('.rh-codigo-input');
  campoCodigo.addEventListener('input', function () {
    campoCodigo.value = campoCodigo.value.replace(/\D/g, '').slice(0, 6);
  });
  campoCodigo.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {
      e.preventDefault();
      confirmarCodigo();
    }
  });

  overlayCodigo.querySelector('.rh-confirmar').addEventListener('click', confirmarCodigo);
  overlayCodigo.querySelector('.rh-voltar').addEventListener('click', function () {
    fecharCodigo(true);
  });
  overlayCodigo.querySelector('.rh-reenviar').addEventListener('click', async function () {
    if (!dadosPendentes) return;
    var envio = await enviarCodigo(dadosPendentes.email, false);
    if (envio) codigoEnviadoPara = dadosPendentes.email;
  });

  overlay.querySelector('.rh-skip').addEventListener('click', submeter);
  overlay.querySelector('.rh-submit').addEventListener('click', submeter);
  overlay.addEventListener('mousedown', function (e) {
    if (e.target === overlay) close();
  });
  // No pop-up do código, clicar fora ou apertar Esc volta para os extras em
  // vez de fechar tudo: o cadastro já foi preenchido e o código já foi enviado.
  overlayCodigo.addEventListener('mousedown', function (e) {
    if (e.target === overlayCodigo) fecharCodigo(true);
  });
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Escape') return;
    if (overlayCodigo.classList.contains('open')) fecharCodigo(true);
    else if (overlay.classList.contains('open')) close();
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
