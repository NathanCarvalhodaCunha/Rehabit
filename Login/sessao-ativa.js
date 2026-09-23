// Rehabit — quem já está logado não vê a tela de login.
//
// A sessão fica no localStorage e sobrevive ao fechamento da aba, mas o site
// sempre abre pelo login (index.html redireciona para cá), e esta tela nunca
// olhava para ela: fechar a aba e voltar parecia um logout. Roda no <head>,
// antes do formulário aparecer, para não piscar a tela de login no caminho.
(function entrarComSessaoSalva() {
  let sessao;
  try {
    sessao = JSON.parse(localStorage.getItem('rehabit_usuario'));
  } catch (err) {
    return;
  }
  if (!sessao || !sessao.token) return;

  // Token vencido (30 dias) vai virar 401 na primeira chamada da home, que
  // devolveria a pessoa para cá depois de esperar a API. Melhor já ficar.
  if (tokenVencido(sessao.token)) {
    localStorage.removeItem('rehabit_usuario');
    return;
  }

  const sufixo = /-escuro\.html/i.test(location.pathname) ? '-escuro' : '';
  const home = sessao.tipo === 'CLINICA' ? 'instituicao' : 'profissional';
  // replace, e não href: com o login no histórico, o "voltar" da home
  // cairia aqui e seria jogado para a frente de novo.
  location.replace(`../Software/${home}${sufixo}.html`);

  // Lê o "exp" do JWT sem validar a assinatura — quem valida é a API. Se não
  // der para ler, deixa a API decidir, como as telas internas já fazem.
  function tokenVencido(token) {
    try {
      const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const { exp } = JSON.parse(atob(payload));
      return typeof exp === 'number' && exp * 1000 <= Date.now();
    } catch (err) {
      return false;
    }
  }
})();
