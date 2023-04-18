import { encode } from './JwtHelper.js'

function getJwtPayload(actAsParty, readAsParty) {

    return {
        "https://daml.com/ledger-api": {
            "ledgerId": "sandbox",
            "applicationId": "HTTP-JSON-API-Gateway",
            "actAs": [actAsParty],
//            "readAs": [readAsParty]
        }
    }
}

export function getHeaderWithToken(actAsParties, readAsParties) {
    return {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${encode(getJwtPayload(actAsParties, readAsParties))}`,
        },
    }
}

const baseUrl = 'http://localhost:7575'

export const createUrl = `${baseUrl}/v1/create`;

export const fetchUrl = `${baseUrl}/v1/fetch`;

export const exerciseUrl = `${baseUrl}/v1/exercise`;