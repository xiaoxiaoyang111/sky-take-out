import test from 'node:test'
import assert from 'node:assert/strict'
import {validateRentalDates} from '../src/rental-dates.js'

test('return day is excluded; one night is one rental day',()=>assert.equal(validateRentalDates('2026-10-27','2026-10-28','2026-10-27'),1))
test('30 days and starts exactly 180 days ahead are allowed',()=>{assert.equal(validateRentalDates('2026-10-27','2026-11-26','2026-10-27'),30);assert.equal(validateRentalDates('2027-04-25','2027-04-26','2026-10-27'),1)})
test('past start, beyond booking window and overlong rental rejected',()=>{for(const [a,b] of [['2026-10-26','2026-10-28'],['2027-04-26','2027-04-27'],['2026-10-27','2026-11-27']])assert.throws(()=>validateRentalDates(a,b,'2026-10-27'))})
test('blank, reversed and impossible dates rejected',()=>{for(const [a,b] of [['',''],['2026-10-28','2026-10-27'],['2026-10-27','2026-10-27'],['2026-02-30','2026-03-01'],['oops','2026-10-29']])assert.throws(()=>validateRentalDates(a,b,'2026-10-27'))})
test('calendar days are unaffected by daylight saving transitions',()=>assert.equal(validateRentalDates('2026-03-07','2026-03-09','2026-03-01'),2))
