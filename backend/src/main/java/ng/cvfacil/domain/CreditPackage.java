package ng.cvfacil.domain;

/** Catálogo fixo de pacotes de créditos (PRD: preços em BRL, centavos para evitar float). */
public enum CreditPackage {
  PACK_3(3, 3000),
  PACK_6(6, 5000),
  PACK_9(9, 7000);

  public final int credits;
  public final int priceCents;

  CreditPackage(int credits, int priceCents) {
    this.credits = credits;
    this.priceCents = priceCents;
  }
}
