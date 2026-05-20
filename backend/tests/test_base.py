"""Unit tests for shared scraper helpers in app.scraper.base."""

import re

import pytest

from app.scraper.base import parse_currency, parse_duration, parse_price


class TestParsePrice:
    def test_dollar_bare(self):
        assert parse_price("$451") == 45100

    def test_dollar_with_comma(self):
        assert parse_price("$1,234") == 123400

    def test_dollar_with_cents(self):
        assert parse_price("$299.99") == 29999

    def test_yen_symbol(self):
        assert parse_price("¥64,651") == 6465100

    def test_euro_symbol(self):
        assert parse_price("€299") == 29900

    def test_pound_symbol(self):
        assert parse_price("£150") == 15000

    def test_google_flights_aria_label(self):
        # "From 64651 Japanese yen round trip total..."
        assert parse_price("From 64651 Japanese yen round trip total.") == 6465100

    def test_google_flights_dollars(self):
        assert parse_price("From $284.50 round trip total.") == 28450

    def test_empty(self):
        assert parse_price("") is None

    def test_no_price(self):
        assert parse_price("Hello world") is None

    def test_zero_dollars(self):
        # Edge case: $0
        result = parse_price("$0")
        # May return 0 or None depending on regex match
        assert result is None or result == 0

    def test_price_in_middle_of_text(self):
        text = "Some text $299.99 with more text"
        assert parse_price(text) == 29999


class TestParseCurrency:
    def test_yen(self):
        assert parse_currency("¥64,651") == "JPY"
        assert parse_currency("Japanese yen") == "JPY"

    def test_euro(self):
        assert parse_currency("€299") == "EUR"
        assert parse_currency("euros") == "EUR"

    def test_pound(self):
        assert parse_currency("£150") == "GBP"
        assert parse_currency("pounds sterling") == "GBP"

    def test_default_usd(self):
        assert parse_currency("$451") == "USD"
        assert parse_currency("just some text") == "USD"


class TestParseDuration:
    def test_compact_hours_minutes(self):
        assert parse_duration("5h 30m") == 330

    def test_compact_hours_only(self):
        assert parse_duration("2h") == 120

    def test_compact_minutes_only(self):
        assert parse_duration("45m") == 45

    def test_expanded_format(self):
        assert parse_duration("8 hr 12 min") == 492

    def test_expanded_hours_only(self):
        assert parse_duration("3 hr") == 180

    def test_mixed_in_text(self):
        assert parse_duration("Total duration 5h 47m") == 347

    def test_no_duration(self):
        assert parse_duration("Hello") is None

    def test_empty(self):
        assert parse_duration("") is None
