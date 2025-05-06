from decimal import Decimal, ROUND_HALF_UP


def remove_exponent(d):
    return d.quantize(Decimal(1)) if d == d.to_integral() else d.normalize()

reference = Decimal("0.0001")
def round_flt_to_4_decimal(flt: float) -> Decimal:
    return Decimal(flt).quantize(reference, rounding=ROUND_HALF_UP)
